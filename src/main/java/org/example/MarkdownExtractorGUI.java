package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownExtractorGUI extends JFrame {

    // 定義兩個正則表達式：一個針對 Markdown 的 ![]()，另一個針對 HTML 的 <img src="">
    private static final String MARKDOWN_IMAGE_REGEX = "!\\[.*?\\]\\((.*?\\.(png|jpg|jpeg|gif|bmp))\\)";
    private static final String HTML_IMAGE_REGEX = "<img\\s+[^>]*src=[\"'](.*?\\.(png|jpg|jpeg|gif|bmp))[\"']";
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(MARKDOWN_IMAGE_REGEX, Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE_PATTERN = Pattern.compile(HTML_IMAGE_REGEX, Pattern.CASE_INSENSITIVE);
    private JButton processButton;
    private JTabbedPane tabbedPane;  // 新增的頁簽
    private Tab1_getImageFolder_MarkdownFile tab1; // 定義 tab1
    private Tab2_autoRestoreRelativeURL tab2; // 定義 tab2
    private Tab3_createOutlineTable tab3; // 定義 tab3

    public MarkdownExtractorGUI() {
        setTitle("Markdown Extractor");
        setSize(650, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 初始化頁簽
        tabbedPane = new JTabbedPane();

        // 初始化 tab1 類別並新增到頁簽
        tab1 = new Tab1_getImageFolder_MarkdownFile(this);
        tabbedPane.addTab("Get images && .md", tab1.createFirstTab());

        // 初始化 tab2 類別並新增到頁簽
        tab2 = new Tab2_autoRestoreRelativeURL(this);
        tabbedPane.addTab("Auto Adjust URL", tab2.createSecondTab());

        // 初始化 tab3 類別並新增到頁簽
        tab3 = new Tab3_createOutlineTable(this);
        tabbedPane.addTab("OutLineCreator",tab3.createThirdTab());


        add(tabbedPane, BorderLayout.CENTER);

        // Process Markdown 按鈕
        processButton = new JButton("Process Markdown");
        processButton.addActionListener(e -> {
            // 判斷當前選中的頁簽是否為第一頁
            int selectedIndex = tabbedPane.getSelectedIndex();
            switch (selectedIndex){
                case 0:
                    tab1.processMarkdown();
                    break;
                case 1:
                    tab2.checkAndFixImagePaths();
                    break;
                case 2:
                    tab3.processMarkdown();
                    break;
            }
        });

        add(processButton, BorderLayout.SOUTH);
    }

    // TransferHandler to handle file and folder drops
    private class FileDropHandler extends TransferHandler {
        private boolean isInput;
        private DefaultListModel<String> ioFolderModel;


        public FileDropHandler(boolean isInput, DefaultListModel<String> ioFolderModel) {
            this.isInput = isInput;
            this.ioFolderModel = ioFolderModel;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            try {
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    for (File selectedFile : files) {
                        if (isInput) {
                            // 檢查是否為文件或資料夾
                            if (selectedFile.isDirectory() || selectedFile.getName().endsWith(".md")) {
                                ioFolderModel.addElement(selectedFile.getAbsolutePath());
                            }
                        } else {
                            if (selectedFile.isDirectory()) {
                                // 如果已有一個輸出資料夾，則清空列表，然後添加新的資料夾
                                if (ioFolderModel.size() > 0) {
                                    ioFolderModel.clear();
                                }
                                ioFolderModel.addElement(selectedFile.getAbsolutePath());
                            }
                        }
                    }
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private class Tab1_getImageFolder_MarkdownFile extends Component {
        private DefaultListModel<String> inputFolderModel;
        private JList<String> inputFolderList;
        private DefaultListModel<String> outputFolderModel;
        private JList<String> outputFolderList;
        private JList<String> failedFilesList;
        private DefaultListModel<String> failedFilesModel;
        private Map<String, List<String>> failedImagesMap;  // Map to store md file and its missing images
        private File outputFolder;

        private JFrame ancestorWindow;
        public Tab1_getImageFolder_MarkdownFile (JFrame ancestorWindow){
            this.ancestorWindow = ancestorWindow;
        }
        private Component createFirstTab() {
            JPanel myBasicPanel = new JPanel(new BorderLayout());
            // 佈局調整：左邊 Input Folder 列表，右邊 Output Folder 單一文本框
            JPanel panelGrid1 = new JPanel(new GridLayout(1, 2, 30, 30));
            panelGrid1.setBorder(new EmptyBorder(10, 10, 10, 10));

            // Input Folder 左邊
            JPanel inputPanelTitle = new JPanel((new BorderLayout()));
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
            inputFolderModel = new DefaultListModel<>();
            inputFolderList = new JList<>(inputFolderModel);
            inputFolderList.setTransferHandler(new FileDropHandler(true,inputFolderModel));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

            inputPanelTitle.add(inputLabel, BorderLayout.WEST);
            inputPanel.add(inputPanelTitle, BorderLayout.NORTH);
            inputPanel.add(inputScrollPane, BorderLayout.CENTER);
            panelGrid1.add(inputPanel);

            // Output Folder 右邊
            JPanel outputPanel = new JPanel(new BorderLayout());
            JLabel outputLabel = new JLabel("Output Folders:");
            outputFolderModel = new DefaultListModel<>();
            outputFolderList = new JList<>(outputFolderModel);
            outputFolderList.setTransferHandler(new FileDropHandler(false, outputFolderModel));  // 支援拖放資料夾
            JScrollPane outputScrollPane = new JScrollPane(outputFolderList);  // 添加 ScrollPane 以支持多個輸出資料夾

            outputPanel.add(outputLabel, BorderLayout.NORTH);
            outputPanel.add(outputScrollPane, BorderLayout.CENTER);
            panelGrid1.add(outputPanel);

            myBasicPanel.add(panelGrid1, BorderLayout.NORTH);

            // Failed files list (with scroll bar)
            JPanel panelGrid2 = new JPanel(new GridLayout(1, 1, 30, 30));
            panelGrid2.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel errorMsgPanel = new JPanel(new BorderLayout());
            JLabel errorMsgLabel = new JLabel("Errors as following:");
            failedFilesModel = new DefaultListModel<>();
            failedFilesList = new JList<>(failedFilesModel);
            JScrollPane scrollPane = new JScrollPane(failedFilesList);
            errorMsgPanel.add(errorMsgLabel, BorderLayout.NORTH);
            errorMsgPanel.add(scrollPane, BorderLayout.CENTER);
            panelGrid2.add(errorMsgPanel);
            myBasicPanel.add(panelGrid2, BorderLayout.CENTER);

            // 添加清空按鈕來清空使用者丟入的檔案或資料夾
            JButton clearButton = new JButton("Clear Input");
            clearButton.addActionListener(e -> {
                inputFolderModel.clear();  // 清空輸入的檔案或資料夾列表
                JOptionPane.showMessageDialog(ancestorWindow, "Input files and folders cleared.");
            });

            // 將 clearButton 添加到界面中的合適位置，例如在 "Process Markdown" 按鈕旁邊
            inputPanelTitle.add(clearButton, BorderLayout.EAST);

            // Mouse click event for the failed files list
            failedFilesList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click
                        String selectedFile = failedFilesList.getSelectedValue();
                        if (selectedFile != null) {
                            displayFailedImages(selectedFile);
                        }
                    }
                }
            });

            failedImagesMap = new HashMap<>();

            // click and alert to remove for inputFolderList
            inputFolderList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click for deletion
                        int selectedIndex = inputFolderList.getSelectedIndex();
                        if (selectedIndex != -1) {
                            String selectedItem = inputFolderModel.getElementAt(selectedIndex);

                            // 彈出確認對話框
                            int response = JOptionPane.showConfirmDialog(
                                    null,
                                    "Remove this item from the target list?\n\n\n" + selectedItem,
                                    "Confirm removal from List",
                                    JOptionPane.YES_NO_OPTION
                            );

                            // 如果使用者選擇了 "YES"，則刪除該項目
                            if (response == JOptionPane.YES_OPTION) {
                                inputFolderModel.remove(selectedIndex);
                            }
                        }
                    }
                }
            });

            // click and open folder location for outputFolderList
            outputFolderList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click
                        int selectedIndex = outputFolderList.getSelectedIndex();
                        if (selectedIndex != -1) {
                            String selectedItem = outputFolderModel.getElementAt(selectedIndex);

                            // 開啟資料夾所在位置
                            try {
                                Desktop.getDesktop().open(new File(selectedItem));
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                                JOptionPane.showMessageDialog(null, "Failed to open folder: " + selectedItem);
                            }
                        }
                    }
                }
            });
            return myBasicPanel;
        }



        private void processMarkdown() {
            if (inputFolderModel.isEmpty() || outputFolderModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please select input and output folders or files.");
                return;
            }

            failedFilesModel.clear();
            failedImagesMap.clear();

            try {
                for (int j = 0; j < outputFolderModel.size(); j++) {
                    File outputFolder = new File(outputFolderModel.get(j));

                    // 在目標資料夾下建立 'markdown' 和 'images' 子資料夾
                    File markdownFolder = new File(outputFolder, "markdown");
                    File imagesFolder = new File(outputFolder, "images");

                    Files.createDirectories(markdownFolder.toPath());
                    Files.createDirectories(imagesFolder.toPath());

                    for (int i = 0; i < inputFolderModel.size(); i++) {
                        File selectedFile = new File(inputFolderModel.get(i));

                        if (selectedFile.isDirectory()) {
                            processDirectory(selectedFile, markdownFolder, imagesFolder);  // 處理資料夾中的 .md 文件
                        } else if (selectedFile.getName().endsWith(".md")) {
                            processMarkdownFile(selectedFile, markdownFolder, imagesFolder);  // 處理單一 .md 文件
                        }
                    }
                }

                JOptionPane.showMessageDialog(ancestorWindow, "Processing completed.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "An error occurred: " + e.getMessage());
            }
        }

        private void processDirectory(File directory, File markdownFolder, File imagesFolder) throws IOException {
            // 遍歷當前資料夾中的所有文件和資料夾
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // 如果是子資料夾，僅在 markdownFolder 創建對應的目標資料夾，不再創建 images 資料夾
                        File newMarkdownFolder = new File(markdownFolder, file.getName());
                        Files.createDirectories(newMarkdownFolder.toPath());
                        // 遞迴處理子資料夾，但只傳入相同的 imagesFolder，圖片不再有層次結構
                        processDirectory(file, newMarkdownFolder, imagesFolder);
                    } else if (file.getName().endsWith(".md")) {
                        // 處理當前目錄下的 .md 文件
                        processMarkdownFile(file, markdownFolder, imagesFolder);
                    }
                }
            }
        }

        private void processMarkdownFile(File file, File markdownFolder, File imagesFolder) throws IOException {
            // 複製 .md 檔案到對應的 markdown 資料夾中
            File markdownDestination = new File(markdownFolder, file.getName());
            Files.copy(file.toPath(), markdownDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String content = new String(Files.readAllBytes(file.toPath()));
            List<String> missingImages = new ArrayList<>();

            // 使用正則表達式來匹配 Markdown 和 HTML 中的圖片
            Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
            Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(content);

            // 處理 Markdown 圖片
            while (markdownMatcher.find()) {
                String imageUrl = markdownMatcher.group(1);
                processImage(file, imageUrl, imagesFolder, missingImages);
            }

            // 處理 HTML 圖片
            while (htmlMatcher.find()) {
                String imageUrl = htmlMatcher.group(1);
                processImage(file, imageUrl, imagesFolder, missingImages);
            }

            if (!missingImages.isEmpty()) {
                failedFilesModel.addElement(file.getAbsolutePath());
                failedImagesMap.put(file.getAbsolutePath(), missingImages);
            }
        }

        private void processImage(File file, String imageUrl, File imagesFolder, List<String> missingImages) {
            // 圖片是相對路徑時，基於 .md 檔案的位置解析
            File imageFile = new File(file.getParentFile(), imageUrl);
            try {
                if (!imageFile.isAbsolute()) {
                    imageFile = imageFile.getCanonicalFile(); // 取得絕對路徑
                }

                if (!imageFile.exists()) {
                    missingImages.add(imageUrl);
                } else {
                    // 所有圖片都複製到統一的 images 資料夾，不再依據來源目錄
                    String imageName = imageFile.getName();
                    File outputFile = new File(imagesFolder, imageName);
                    copyImage(imageFile, outputFile);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void copyImage(File imageFile, File outputFile) {
            try {
                Files.copy(imageFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to copy image: " + imageFile.getAbsolutePath());
            }
        }

        // 顯示失敗的圖片
        private void displayFailedImages(String mdFile) {
            List<String> missingImages = failedImagesMap.get(mdFile);
            if (missingImages != null) {
                StringBuilder sb = new StringBuilder();
                for (String img : missingImages) {
                    sb.append(img).append("\n");
                }

                JTextArea textArea = new JTextArea(sb.toString());
                textArea.setEditable(false);

                JScrollPane scrollPane = new JScrollPane(textArea);

                JOptionPane.showMessageDialog(this, scrollPane, "Missing Images in " + mdFile, JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class Tab2_autoRestoreRelativeURL extends Component {

        private DefaultListModel<String> inputFolderModel;  // 支援拖曳輸入 .md 檔案或資料夾
        private JList<String> inputFolderList;
        private DefaultListModel<String> imagesFolderModel;  // images 資料夾的模型
        private JList<String> imagesFolderList;
        private DefaultListModel<String> rootFolderModel;  // root 資料夾的模型
        private JList<String> rootFolderList;
        private DefaultListModel<String> errorLogModel;
        private JList<String> errorLogList;
        private File imagesFolder;
        private File rootFolder;
        private JFrame ancestorWindow;
        private Map<String, List<String>> errorDetailsMap;  // Map to store detailed errors for each file

        public Tab2_autoRestoreRelativeURL(JFrame ancestorWindow) {
            this.ancestorWindow = ancestorWindow;
            this.errorDetailsMap = new HashMap<>();  // Initialize error details map
        }

        private Component createSecondTab() {
            JPanel myBasicPanel = new JPanel(new GridLayout(2, 1, 10, 10));

            // 佈局調整：左邊 Input Folder 列表，右邊 Root Folder 和 Images Folder
            JPanel panelGridMain = new JPanel(new GridLayout(1, 2, 10, 10));
            panelGridMain.setBorder(new EmptyBorder(10, 10, 0, 10));

            // Input Folder 左邊
            JPanel inputPanelTitle = new JPanel((new BorderLayout()));
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
            inputFolderModel = new DefaultListModel<>();
            inputFolderList = new JList<>(inputFolderModel);
            inputFolderList.setTransferHandler(new FileDropHandler(true, inputFolderModel));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

            // Input Folder  >> 標題文字 :  按鈕  ， 下方為丟檔案進去的區塊
            inputPanelTitle.add(inputLabel, BorderLayout.WEST);
            inputPanel.add(inputPanelTitle, BorderLayout.NORTH);
            inputPanel.add(inputScrollPane, BorderLayout.CENTER);
            panelGridMain.add(inputPanel);

            JPanel panelGridR = new JPanel(new GridLayout(2, 1, 10, 10));
            panelGridR.setBorder(new EmptyBorder(0, 10, 0, 10));

            // Root Folder 中間
            JPanel rootPanel = new JPanel(new BorderLayout());
            JLabel rootLabel = new JLabel("Root Folder: images & target's root");
            rootFolderModel = new DefaultListModel<>();
            rootFolderList = new JList<>(rootFolderModel);
            rootFolderList.setTransferHandler(new FileDropHandler(false, rootFolderModel));  // 支援拖放資料夾
            JScrollPane rootScrollPane = new JScrollPane(rootFolderList);  // 支援多個根目錄
            rootPanel.add(rootLabel, BorderLayout.NORTH);
            rootPanel.add(rootScrollPane, BorderLayout.CENTER);
            panelGridR.add(rootPanel);

            // Images Folder 右邊
            JPanel imagesPanel = new JPanel(new BorderLayout());
            JLabel imagesLabel = new JLabel("Images Folder:");
            imagesFolderModel = new DefaultListModel<>();
            imagesFolderList = new JList<>(imagesFolderModel);
            imagesFolderList.setTransferHandler(new FileDropHandler(false, imagesFolderModel));  // 支援拖放資料夾
            JScrollPane imagesScrollPane = new JScrollPane(imagesFolderList);  // 支援多個圖片資料夾
            imagesPanel.add(imagesLabel, BorderLayout.NORTH);
            imagesPanel.add(imagesScrollPane, BorderLayout.CENTER);
            panelGridR.add(imagesPanel);

            panelGridMain.add(panelGridR, BorderLayout.EAST);

            myBasicPanel.add(panelGridMain, BorderLayout.NORTH);

            // Failed files list (with scroll bar)
            JPanel panelGrid2 = new JPanel(new GridLayout(1, 1, 30, 30));
            panelGrid2.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel errorMsgPanel = new JPanel(new BorderLayout());
            JLabel errorMsgLabel = new JLabel("Errors as following:");
            errorLogModel = new DefaultListModel<>();
            errorLogList = new JList<>(errorLogModel);
            JScrollPane errorScrollPane = new JScrollPane(errorLogList);
            errorMsgPanel.add(errorMsgLabel, BorderLayout.NORTH);
            errorMsgPanel.add(errorScrollPane, BorderLayout.CENTER);
            panelGrid2.add(errorMsgPanel);
            myBasicPanel.add(panelGrid2, BorderLayout.CENTER);

            // 添加清空按鈕來清空使用者丟入的檔案或資料夾
            JButton clearButton = new JButton("Clear Input");
            clearButton.addActionListener(e -> {
                inputFolderModel.clear();  // 清空輸入的檔案或資料夾列表
                JOptionPane.showMessageDialog(ancestorWindow, "Input files and folders cleared.");
            });

            // 將 clearButton 添加到界面中的合適位置。
            inputPanelTitle.add(clearButton, BorderLayout.EAST);

            // Mouse click event for the error log list
            errorLogList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click
                        String selectedFile = errorLogList.getSelectedValue();
                        if (selectedFile != null) {
                            displayErrorDetails(selectedFile);  // 顯示錯誤詳細訊息
                        }
                    }
                }
            });

            // click and alert to remove for inputFolderList
            inputFolderList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click for deletion
                        int selectedIndex = inputFolderList.getSelectedIndex();
                        if (selectedIndex != -1) {
                            String selectedItem = inputFolderModel.getElementAt(selectedIndex);

                            // 彈出確認對話框
                            int response = JOptionPane.showConfirmDialog(
                                    null,
                                    "Remove this item from the target list?\n\n\n" + selectedItem,
                                    "Confirm removal from List",
                                    JOptionPane.YES_NO_OPTION
                            );

                            // 如果使用者選擇了 "YES"，則刪除該項目
                            if (response == JOptionPane.YES_OPTION) {
                                inputFolderModel.remove(selectedIndex);
                            }
                        }
                    }
                }
            });

            // click and alert to remove for inputFolderList
            inputFolderList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {  // Detect double-click for deletion
                        int selectedIndex = inputFolderList.getSelectedIndex();
                        if (selectedIndex != -1) {
                            String selectedItem = inputFolderModel.getElementAt(selectedIndex);

                            // 彈出確認對話框
                            int response = JOptionPane.showConfirmDialog(
                                    null,
                                    "Remove this item from the target list?\n\n\n" + selectedItem,
                                    "Confirm removal from List",
                                    JOptionPane.YES_NO_OPTION
                            );

                            // 如果使用者選擇了 "YES"，則刪除該項目
                            if (response == JOptionPane.YES_OPTION) {
                                inputFolderModel.remove(selectedIndex);
                            }
                        }
                    }
                }
            });

            return myBasicPanel;
        }

        // 獨立檢查方法，用於檢查是否在 rootFolder 下
        private boolean isUnderRootFolder(File file, File rootFolder, String fileType) {
            try {
                Path filePath = file.getCanonicalFile().toPath();
                Path rootFolderPath = rootFolder.getCanonicalFile().toPath();

                if (!filePath.startsWith(rootFolderPath)) {
                    JOptionPane.showMessageDialog(ancestorWindow, "The " + fileType + " is outside the root folder. Unable to compute relative path.", "Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "An error occurred while checking path for " + fileType + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            return true;
        }
        // 檢查並修正圖片路徑
        private void checkAndFixImagePaths() {
            if (inputFolderModel.isEmpty() || imagesFolderModel.isEmpty() || rootFolderModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please select input folders, an images folder, and a root folder.");
                return;
            }

            errorLogModel.clear();
            errorDetailsMap.clear();

            imagesFolder = new File(imagesFolderModel.get(0));  // 取得 images 資料夾
            rootFolder = new File(rootFolderModel.get(0));  // 取得 root 資料夾

            // 檢查圖片資料夾是否位於 rootFolder 之下
            if (!isUnderRootFolder(imagesFolder, rootFolder, "images folder")) {
                return;  // 如果圖片資料夾不在 root folder 下，停止處理
            }

            // 檢查所有輸入的 .md 文件或資料夾是否位於 rootFolder 之下
            for (int i = 0; i < inputFolderModel.size(); i++) {
                File selectedFile = new File(inputFolderModel.get(i));

                // 如果選中的文件或資料夾不在 rootFolder 之下，跳出提示並停止處理
                if (!isUnderRootFolder(selectedFile, rootFolder, ".md file or folder")) {
                    return;
                }
            }

            try {
                // 開始處理所有的 .md 文件
                for (int i = 0; i < inputFolderModel.size(); i++) {
                    File selectedFile = new File(inputFolderModel.get(i));

                    if (selectedFile.isDirectory()) {
                        // 遍歷資料夾中的 .md 檔案
                        Files.walk(selectedFile.toPath())
                                .filter(path -> path.toString().endsWith(".md"))
                                .forEach(this::processAndFixMarkdownFile);
                    } else if (selectedFile.getName().endsWith(".md")) {
                        // 處理單一 .md 檔案
                        processAndFixMarkdownFile(selectedFile.toPath());
                    }
                }
                // 所有 .md 文件處理完成後顯示提示
                JOptionPane.showMessageDialog(ancestorWindow, "Processing completed.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "An error occurred: " + e.getMessage());
            }
        }

        private void processAndFixMarkdownFile(Path mdFilePath) {
            try {
                String content = new String(Files.readAllBytes(mdFilePath));
                StringBuilder updatedContent = new StringBuilder();
                int lastIndex = 0; // 用來追蹤上次匹配結束的位置
                List<String> missingImages = new ArrayList<>();
                boolean contentModified = false; // 標記文件內容是否變更

                // 處理 Markdown 圖片
                Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
                while (markdownMatcher.find()) {
                    updatedContent.append(content, lastIndex, markdownMatcher.start(1));
                    String imageUrl = markdownMatcher.group(1);

                    // 調試訊息：打印圖片的原始路徑
                    // // System.out.println("檢查圖片路徑: " + imageUrl);

                    // 首先檢查圖片是否存在
                    if (isImagePathValid(mdFilePath.getParent().toFile(), imageUrl)) {
                        updatedContent.append(imageUrl);  // 路徑正確，保持原路徑
                        // // System.out.println("圖片存在: " + imageUrl);
                    } else {
                        // 嘗試修正圖片路徑
                        String fixedUrl = fixImagePath(mdFilePath.getParent().toFile(), imageUrl);
                        if (fixedUrl != null) {
                            updatedContent.append(fixedUrl); // 插入修正後的路徑
                            contentModified = true; // 標記內容已變更
                            // // System.out.println("修正圖片路徑: " + fixedUrl);
                        } else {
                            updatedContent.append(imageUrl); // 如果修正失敗，保持原路徑
                            missingImages.add(imageUrl);  // 記錄缺失的圖片
                            System.out.println("圖片丟失: " + imageUrl);
                        }
                    }
                    lastIndex = markdownMatcher.end(1); // 更新 lastIndex
                }
                updatedContent.append(content.substring(lastIndex));  // 添加剩餘部分

                // 處理 HTML 圖片 (同理)
                lastIndex = 0;
                Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(updatedContent.toString());
                StringBuilder finalContent = new StringBuilder();
                while (htmlMatcher.find()) {
                    finalContent.append(updatedContent, lastIndex, htmlMatcher.start(1));
                    String imageUrl = htmlMatcher.group(1);

                    // System.out.println("檢查 HTML 圖片路徑: " + imageUrl);

                    // 檢查圖片是否存在
                    if (isImagePathValid(mdFilePath.getParent().toFile(), imageUrl)) {
                        finalContent.append(imageUrl); // 路徑正確，保持原路徑
                        // System.out.println("HTML 圖片存在: " + imageUrl);
                    } else {
                        // 嘗試修正圖片路徑
                        String fixedUrl = fixImagePath(mdFilePath.getParent().toFile(), imageUrl);
                        if (fixedUrl != null) {
                            finalContent.append(fixedUrl); // 插入修正後的路徑
                            contentModified = true; // 標記內容已變更
                            // System.out.println("修正 HTML 圖片路徑: " + fixedUrl);
                        } else {
                            finalContent.append(imageUrl); // 如果修正失敗，保持原路徑
                            missingImages.add(imageUrl);  // 記錄缺失的圖片
                            System.out.println("HTML 圖片丟失: " + imageUrl);
                        }
                    }
                    lastIndex = htmlMatcher.end(1); // 更新 lastIndex
                }
                finalContent.append(updatedContent.substring(lastIndex));  // 添加剩餘部分

                // 如果內容變更，才寫回文件
                if (contentModified) {
                    Files.write(mdFilePath, finalContent.toString().getBytes());
                }

                // 如果有缺失的圖片，記錄到錯誤日誌
                if (!missingImages.isEmpty()) {
                    errorLogModel.addElement(mdFilePath.toString());
                    errorDetailsMap.put(mdFilePath.toString(), missingImages);
                }

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "An error occurred while processing: " + mdFilePath.toString());
            }
        }




        // 修正相對路徑
        // 修正相對路徑
        private String fixImagePath(File mdFileParent, String imagePath) {
            File imageFile = new File(mdFileParent, imagePath);

            if (!imageFile.exists()) {
                File fixedImageFile = new File(imagesFolder, new File(imagePath).getName());
                if (fixedImageFile.exists()) {
                    try {
                        // 取得 .md 檔案與 images 資料夾的絕對路徑
                        Path mdFilePath = mdFileParent.getCanonicalFile().toPath();
                        Path imagesFolderPath = imagesFolder.getCanonicalFile().toPath();

                        // 根目錄一致時計算相對路徑
                        Path relativePath = mdFilePath.relativize(imagesFolderPath.resolve(fixedImageFile.getName()));
                        return relativePath.toString().replace("\\", "/");  // 返回計算出的相對路徑，使用正確的分隔符
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;  // 若無法修正，返回 null
        }


        /// 注意，不可以用
        // 檢查圖片路徑是否正確
        private boolean isImagePathValid(File mdFileParent, String imagePath) {

        //  不可以使用，因為下面這種方式會誤判，明明圖片URL路徑是正確，卻印出說有錯誤。
        //  File imageFile = new File(mdFileParent, imagePath);
        //  return imageFile.exists() && imageFile.getParentFile().equals(imagesFolder);

        // 下面才是正確的作法
            File imageFile = new File(mdFileParent, imagePath);
            try {
                // 如果圖片路徑是相對路徑，將其轉換為絕對路徑來檢查
                if (!imageFile.isAbsolute()) {
                    imageFile = new File(mdFileParent, imagePath).getCanonicalFile();
                }
                // 檢查文件是否存在，存在返回 true
                return imageFile.exists();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }


        // 顯示錯誤圖片詳細資訊
        private void displayErrorDetails(String mdFile) {
            List<String> missingImages = errorDetailsMap.get(mdFile);
            if (missingImages != null) {
                StringBuilder sb = new StringBuilder();
                for (String img : missingImages) {
                    sb.append(img).append("\n");
                }

                JTextArea textArea = new JTextArea(sb.toString());
                textArea.setEditable(false);

                JScrollPane scrollPane = new JScrollPane(textArea);

                JOptionPane.showMessageDialog(this, scrollPane, "Missing Images in " + mdFile, JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class Tab3_createOutlineTable extends Component {
        private DefaultListModel<String> inputFolderModel;  // 支援拖曳輸入 .md 檔案或資料夾
        private JList<String> inputFolderList;
        private JSpinner levelSpinner;  // 用於選擇解析的標題層級
        private JFrame ancestorWindow;
        private JCheckBox includeNumberingCheckbox; // 新增一個打勾框，控制是否顯示編號
        public Tab3_createOutlineTable(JFrame ancestorWindow) {
            this.ancestorWindow = ancestorWindow;
        }

        private Component createThirdTab() {
            JPanel myBasicPanel = new JPanel(new BorderLayout());

            // 佈局調整：上方 Input Folder 列表，底部選擇解析層級的元件
            JPanel panelGridMain = new JPanel(new BorderLayout(10, 10));
            panelGridMain.setBorder(new EmptyBorder(10, 10, 10, 10));

            // Input Folder
            JPanel inputPanelTitle = new JPanel((new BorderLayout()));
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
            inputFolderModel = new DefaultListModel<>();
            inputFolderList = new JList<>(inputFolderModel);
            inputFolderList.setTransferHandler(new FileDropHandler(true, inputFolderModel));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源
            inputPanelTitle.add(inputLabel, BorderLayout.WEST);

            inputPanel.add(inputPanelTitle, BorderLayout.NORTH);
            inputPanel.add(inputScrollPane, BorderLayout.CENTER);

            panelGridMain.add(inputPanel, BorderLayout.CENTER);

            // 標題層級選擇
            JPanel controlPanel = new JPanel(new BorderLayout());
            JLabel levelLabel = new JLabel("Select Heading Level to Parse:");
            levelSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));  // 支援選擇解析的層級，預設為3層 (#, ##, ###)
            controlPanel.add(levelLabel, BorderLayout.NORTH);
            controlPanel.add(levelSpinner, BorderLayout.CENTER);

            // 新增打勾框來選擇是否顯示編號
            includeNumberingCheckbox = new JCheckBox("Include Numbering");
            controlPanel.add(includeNumberingCheckbox, BorderLayout.SOUTH); // 把勾選框加到下方

            panelGridMain.add(controlPanel, BorderLayout.SOUTH);

            myBasicPanel.add(panelGridMain, BorderLayout.CENTER);

            // 添加清空按鈕來清空使用者丟入的檔案或資料夾
            JButton clearButton = new JButton("Clear Input");
            clearButton.addActionListener(e -> {
                inputFolderModel.clear();  // 清空輸入的檔案或資料夾列表
                JOptionPane.showMessageDialog(ancestorWindow, "Input files and folders cleared.");
            });

            // 將 clearButton 添加到界面中的合適位置。
            inputPanelTitle.add(clearButton, BorderLayout.EAST);

            return myBasicPanel;
        }

        // 點擊 "Process Markdown" 時的處理邏輯
        public void processMarkdown() {
            if (inputFolderModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please select input folders or files.");
                return;
            }

            // 獲取使用者選擇的解析層級
            int selectedLevel = (int) levelSpinner.getValue();

            try {
                for (int i = 0; i < inputFolderModel.size(); i++) {
                    File selectedFile = new File(inputFolderModel.get(i));

                    if (selectedFile.isDirectory()) {
                        // 處理資料夾中的 .md 文件
                        processDirectory(selectedFile, selectedLevel);
                    } else if (selectedFile.getName().endsWith(".md")) {
                        // 處理單一 .md 文件
                        processMarkdownFile(selectedFile, selectedLevel);
                    }
                }

                JOptionPane.showMessageDialog(ancestorWindow, "Processing completed.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "An error occurred: " + e.getMessage());
            }
        }

        // 對 Markdown 檔案進行解析並插入表格
        private void processMarkdownFile(File file, int selectedLevel) throws IOException {
            String content = new String(Files.readAllBytes(file.toPath()));

            // 檢查是否已經有「# 大綱 表格」區塊
            String updatedContent = removeOldOutlineTable(content);

            // 解析新內容並生成表格
            List<String[]> headers = parseMarkdownContent(updatedContent, selectedLevel);

            if (!headers.isEmpty()) {
                String table = generateMarkdownTable(headers, selectedLevel);  // 產生表格

                // 將新表格插入「# 大綱 表格」區塊的頂部
                updatedContent = "# OUTLINE \n" + table + "\n\n" + updatedContent;
            }

            // 將更新後的內容寫回文件
            Files.write(file.toPath(), updatedContent.getBytes());
        }

        // 移除舊的「# OUTLINE」區塊，包括表格內容和 # OUTLINE 標題本身
        private String removeOldOutlineTable(String content) {
            StringBuilder updatedContent = new StringBuilder();
            boolean inOutlineSection = false;  // 判斷是否在「大綱 表格」區域
            String[] lines = content.split("\n");

            for (String line : lines) {
                // 偵測到 # OUTLINE，進入「大綱 表格」區域並不加入這行內容
                if (line.startsWith("# OUTLINE")) {
                    inOutlineSection = true;  // 進入「大綱 表格」區域
                    continue;  // 跳過「# OUTLINE」這行
                }

                // 偵測到下一個標題時，退出「大綱 表格」區域
                if (inOutlineSection && line.startsWith("#") && !line.startsWith("# OUTLINE")) {
                    inOutlineSection = false;  // 退出「大綱 表格」區域
                }

                // 只有不在「大綱 表格」區域時才將內容加入 updatedContent
                if (!inOutlineSection) {
                    updatedContent.append(line).append("\n");
                }
            }

            return updatedContent.toString();
        }

        private void processDirectory(File directory, int selectedLevel) throws IOException {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        processDirectory(file, selectedLevel);  // 遞迴處理子資料夾
                    } else if (file.getName().endsWith(".md")) {
                        processMarkdownFile(file, selectedLevel);  // 處理 .md 文件
                    }
                }
            }
        }

        // 生成 Markdown 表格
        private String generateMarkdownTable(List<String[]> headers, int maxLevel) {
            StringBuilder sb = new StringBuilder();

            // 檢查每一層是否有內容，動態調整 maxLevel
            maxLevel = adjustMaxLevel(headers, maxLevel);
            boolean includeNumbering = includeNumberingCheckbox.isSelected(); // 確認是否需要編號

            // 根據 maxLevel 動態生成表格標題
            sb.append("|");
            if (includeNumbering) {
                sb.append(" 編號 |"); // 如果勾選了顯示編號，添加編號列
            }
            for (int i = 1; i <= maxLevel; i++) {
                sb.append(" 標題").append(i).append(" |");
            }
            sb.append("\n|");

            // 根據 maxLevel 動態生成分隔行
            if (includeNumbering) {
                sb.append(" --- |"); // 編號列的分隔行
            }
            for (int i = 1; i <= maxLevel; i++) {
                sb.append(" --- |");
            }
            sb.append("\n");

            // 用來存儲上一行的標題，追踪重複的標題並保持空白
            String[] previousRow = new String[maxLevel];
            Arrays.fill(previousRow, ""); // 確保每個元素被設置為空字串
            // 填寫表格內容
            int rowCount = 1; // 用來生成編號的計數器
            for (String[] row : headers) {
                boolean isNewParent = row[0] != null && !row[0].trim().isEmpty() && !row[0].equals(previousRow[0]); // 判斷是否為新的父標題

                sb.append("| ");
                if (includeNumbering && isNewParent) {
                    sb.append(rowCount++).append(". | "); // 只有當「標題1」有內容且不同於上一行時，才生成編號
                } else if (includeNumbering) {
                    sb.append(" | "); // 否則保持編號欄空白
                }

                for (int i = 0; i < maxLevel; i++) {
                    // 父標題相同時保持空白
                    if (row[i] != null && !row[i].equals(previousRow[i])) {
                        sb.append(row[i]).append(" | ");
                        previousRow[i] = row[i]; // 更新上一行的內容
                    } else {
                        sb.append(" | "); // 空白單元格
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        }

        // 調整 maxLevel，確保只生成有內容的欄位
        private int adjustMaxLevel(List<String[]> headers, int maxLevel) {
            // 檢查每一層是否有內容
            for (int level = maxLevel - 1; level >= 0; level--) {
                boolean hasContent = false;
                for (String[] row : headers) {
                    if (row[level] != null && !row[level].trim().isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
                if (!hasContent) {
                    maxLevel--; // 如果該層沒有內容，減少 maxLevel
                } else {
                    break; // 找到有內容的層級後，停止繼續減少
                }
            }
            return maxLevel;
        }




        // 解析 Markdown 內容，根據選擇的層級提取標題
        private List<String[]> parseMarkdownContent(String content, int maxLevel) {
            List<String[]> headers = new ArrayList<>();
            String[] currentRow = new String[maxLevel];
            Arrays.fill(currentRow, "");

            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("# ") && maxLevel >= 1) {
                    currentRow = new String[maxLevel]; // 清空當前行
                    Arrays.fill(currentRow, ""); // 清空行中的每個層級
                    currentRow[0] = line.substring(2).trim(); // 儲存第一層標題
                    headers.add(currentRow.clone());
                } else if (line.startsWith("## ") && maxLevel >= 2) {
                    currentRow[1] = line.substring(3).trim(); // 儲存第二層標題
                    for (int i = 2; i < maxLevel; i++) currentRow[i] = ""; // 清空更低層級的資料
                    headers.add(currentRow.clone());
                } else if (line.startsWith("### ") && maxLevel >= 3) {
                    currentRow[2] = line.substring(4).trim(); // 儲存第三層標題
                    for (int i = 3; i < maxLevel; i++) currentRow[i] = ""; // 清空更低層級的資料
                    headers.add(currentRow.clone());
                } else if (line.startsWith("#### ") && maxLevel >= 4) {
                    currentRow[3] = line.substring(5).trim(); // 儲存第三層標題
                    for (int i = 4; i < maxLevel; i++) currentRow[i] = ""; // 清空更低層級的資料
                    headers.add(currentRow.clone());
                } else if (line.startsWith("##### ") && maxLevel >= 5) {
                    currentRow[4] = line.substring(6).trim(); // 儲存第三層標題
                    for (int i = 5; i < maxLevel; i++) currentRow[i] = ""; // 清空更低層級的資料
                    headers.add(currentRow.clone());
                } else if (line.startsWith("###### ") && maxLevel >= 6) {
                    currentRow[5] = line.substring(7).trim(); // 儲存第三層標題
                    for (int i = 6; i < maxLevel; i++) currentRow[i] = ""; // 清空更低層級的資料
                    headers.add(currentRow.clone());
                }
            }

            return headers;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MarkdownExtractorGUI().setVisible(true));
    }
}
