package com.intellij.plugin.quickhotfix;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Michal Hájek, <a href="mailto:michalhajek@centrum.cz">michalhajek@centrum.cz</a>
 * @since 8.2.12
 */
public class QuickHotfixCommitExecutor implements CommitExecutor, ProjectComponent, JDOMExternalizable {

    private final Project project;
    private final ChangeListManager changeListManager;

    private QuickHotfixCommitExecutor(final Project project, final ChangeListManager changeListManager) {
        this.project = project;
        this.changeListManager = changeListManager;
    }

    public void initComponent() {
        // nic
    }

    public void disposeComponent() {
        // nic
    }

    @Nls
    public String getActionText() {
        return "Quick hotfix...";
    }

    @NotNull
    public CommitSession createCommitSession() {
        return new QuickHotfixCommitSession();
    }

    public void readExternal(final Element element) throws InvalidDataException {
        DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
        DefaultJDOMExternalizer.writeExternal(this, element);
    }

    public void projectOpened() {
        changeListManager.registerCommitExecutor(this);
    }

    public void projectClosed() {
        // nic
    }

    @NotNull
    public String getComponentName() {
        return "QuickHotfixCommitExecutor";
    }

    //--- CommitSession ------------------------------------------------------------------------------------------------

    public class QuickHotfixCommitSession implements CommitSession {

        private final QuickHotfixSaveConfiguration saveConfiguration;
        private String quickHotfixPath;

        private QuickHotfixCommitSession() {
            this.saveConfiguration = new QuickHotfixSaveConfiguration();
        }

        @Nullable
        public JComponent getAdditionalConfigurationUI() {
            return saveConfiguration.getContentPane();
        }

        public JComponent getAdditionalConfigurationUI(final Collection<Change> collection, final String s) {
            if (quickHotfixPath == null || quickHotfixPath.length() == 0) {
                // noinspection ConstantConditions
                quickHotfixPath = project.getBaseDir().getPresentableUrl();
            }
            File file = ShelveChangesManager.suggestPatchName(project, s, new File(quickHotfixPath), null);
            if (!file.getName().endsWith(".zip")) {
                String path = file.getPath();
                if (path.lastIndexOf('.') >= 0) {
                    path = path.substring(0, path.lastIndexOf('.'));
                }
                file = new File(path + ".zip");
            }
            saveConfiguration.setFileName(file);
            return saveConfiguration.getContentPane();
        }

        public boolean canExecute(final Collection<Change> changes, final String s) {
            return true;
        }

        public void execute(final Collection<Change> changes, final String s) {
            try {
                Set<VirtualFile> vcsRoots = new HashSet<VirtualFile>();

                for (Change change : changes) {
                    ContentRevision afterRevision = change.getAfterRevision();
                    if (afterRevision != null) {
                        FilePath path = afterRevision.getFile();
                        VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, path);
                        vcsRoots.add(vcsRoot);
                    }
                }
                if (!vcsRoots.isEmpty()) {
                    Iterator<VirtualFile> fileIterator = vcsRoots.iterator();
                    VirtualFile root = fileIterator.next();

                    while (fileIterator.hasNext()) {
                        root = VfsUtil.getCommonAncestor(root, fileIterator.next());
                    }

                    if (root != null) {
                        QuickHotfixProgressIndicator progressIndicator = new QuickHotfixProgressIndicator(saveConfiguration.getFileName(), root, changes);
                        ProgressManager.getInstance().runProcess(progressIndicator, progressIndicator);
                    } else {
                        Messages.showErrorDialog(project, "No common ancestor found for changes.", "Error");
                    }
                } else {
                    Messages.showErrorDialog(project, "No VCS roots found.", "Error");
                }
            } catch (Exception e) {
                Messages.showErrorDialog(project, e.getMessage(), "Error");
            }
        }

        public void executionCanceled() {
            // nic
        }

        public String getHelpId() {
            return null;
        }

    }

    //--- Filter -------------------------------------------------------------------------------------------------------

    private class ClassnameFilter implements FilenameFilter {
        private String classname;
        private String classnameToMatch;

        public ClassnameFilter(final String javaFileName) {
            this.classname = javaFileName.replaceAll(".java", ".class");
            String[] splitted = javaFileName.split(".java");
            this.classnameToMatch = (splitted[0] + "$");
        }

        public boolean accept(final File file, final String string) {
            return (string.equalsIgnoreCase(this.classname)) || (string.contains(this.classnameToMatch));
        }
    }

    private class QuickHotfixProgressIndicator extends ProgressIndicatorBase implements Runnable {

        private static final String SRC_MAIN_TEST_JAVA_RESOURCES_REGEX = "src/[main|test]+/[java|resources]+";

        private final String fileName;
        private final VirtualFile root;
        private final Collection<Change> changes;

        private QuickHotfixProgressIndicator(final String fileName, final VirtualFile root, final Collection<Change> changes) {
            this.fileName = fileName;
            this.root = root;
            this.changes = changes;
            setIndeterminate(true);
        }

        public void copyIsToOs(final File file, final OutputStream os) throws IOException {
            InputStream is = new FileInputStream(file);
            try {
                byte[] buf = new byte[1024];
                int i;
                while ((i = is.read(buf)) != -1) {
                    os.write(buf, 0, i);
                }
            } catch (IOException e) {
                throw e;
            } finally {
                is.close();
            }
        }

        private String trimSrcMainTestJavaResources(final String relativePath) {
            return relativePath.replaceFirst(SRC_MAIN_TEST_JAVA_RESOURCES_REGEX + '/', "");
        }

        public void run() {
            try {
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fileName));
                for (Change change : changes) {

                    ContentRevision afterRevision = change.getAfterRevision();
                    if (afterRevision != null) {

                        VirtualFile virtualFile = change.getVirtualFile();
                        if (virtualFile != null) {

                            FilePath file = afterRevision.getFile();
                            String relativePath = VfsUtil.getRelativePath(file.getVirtualFile(), root, '/');

                            if (!virtualFile.isDirectory()) {

                                if (virtualFile.getExtension().equals("java")) {

                                    final Pattern SRC_MAIN_TEST_JAVA_RESOURCES_PATTERN = Pattern.compile(SRC_MAIN_TEST_JAVA_RESOURCES_REGEX);
                                    String path = virtualFile.getParent().getPath();
                                    Matcher srcMainTestJavaMatcher = SRC_MAIN_TEST_JAVA_RESOURCES_PATTERN.matcher(path);
                                    if (srcMainTestJavaMatcher.find()) {

                                        String pathname = srcMainTestJavaMatcher.replaceFirst("target/classes");
                                        File dir = new File(pathname);
                                        File[] files = dir.listFiles(new ClassnameFilter(virtualFile.getName()));
                                        if (files != null) {
                                            for (File clazz : files) {

                                                String name = trimSrcMainTestJavaResources(relativePath);
                                                name = name.replace(file.getName(), clazz.getName());
                                                zos.putNextEntry(new ZipEntry(name));
                                                copyIsToOs(clazz, zos);

                                            }
                                        }

                                    } else {

                                        System.out.println();

                                    }

                                } else {
                                    String name = trimSrcMainTestJavaResources(relativePath);
                                    zos.putNextEntry(new ZipEntry(name));
                                    copyIsToOs(new File(file.getPath()), zos);
                                }

                            }

                        }

                    }

                }
                zos.close();
            } catch (Exception e) {
                Messages.showErrorDialog(project, e.getMessage(), "Error");
            }
        }
    }

}
