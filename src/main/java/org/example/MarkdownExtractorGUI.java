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
    private Tab3_manualRestoreRelativeURL tab3; // 定義 tab3

    public MarkdownExtractorGUI() {
        setTitle("Markdown Extractor");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 初始化頁簽
        tabbedPane = new JTabbedPane();

        // 初始化 tab1 類別並新增到頁簽
        tab1 = new Tab1_getImageFolder_MarkdownFile(this);
        tabbedPane.addTab("Get images && .md", tab1.createFirstTab());

        tab2 = new Tab2_autoRestoreRelativeURL(this);
        tabbedPane.addTab("Auto Adjust URL", tab2.createSecondTab());
        // tabbedPane.addTab("第三頁", new JPanel());

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
            }
        });

        add(processButton, BorderLayout.SOUTH);
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
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
            inputFolderModel = new DefaultListModel<>();
            inputFolderList = new JList<>(inputFolderModel);
            inputFolderList.setTransferHandler(new FileDropHandler(true,inputFolderModel));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

            inputPanel.add(inputLabel, BorderLayout.NORTH);
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
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".md"));
            if (files != null) {
                for (File file : files) {
                    processMarkdownFile(file, markdownFolder, imagesFolder);
                }
            }
        }

        private void processMarkdownFile(File file, File markdownFolder, File imagesFolder) throws IOException {
            // 複製 .md 檔案到 markdown 資料夾
            File markdownDestination = new File(markdownFolder, file.getName());
            Files.copy(file.toPath(), markdownDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String content = new String(Files.readAllBytes(file.toPath()));
            List<String> missingImages = new ArrayList<>();

            // 使用兩個正則表達式來匹配 Markdown 和 HTML 中的圖片
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
            // 如果圖片是相對路徑，則基於 .md 檔案的位置解析
            File imageFile = new File(file.getParentFile(), imageUrl);
            try {
                if (!imageFile.isAbsolute()) {
                    imageFile = imageFile.getCanonicalFile(); // 取得絕對路徑
                }

                if (!imageFile.exists()) {
                    missingImages.add(imageUrl);
                } else {
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
                                    System.out.println("已經除");
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
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
            inputFolderModel = new DefaultListModel<>();
            inputFolderList = new JList<>(inputFolderModel);
            inputFolderList.setTransferHandler(new FileDropHandler(true, inputFolderModel));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源
            inputPanel.add(inputLabel, BorderLayout.NORTH);
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
                    System.out.println("檢查圖片路徑: " + imageUrl);

                    // 首先檢查圖片是否存在
                    if (isImagePathValid(mdFilePath.getParent().toFile(), imageUrl)) {
                        updatedContent.append(imageUrl);  // 路徑正確，保持原路徑
                        System.out.println("圖片存在: " + imageUrl);
                    } else {
                        // 嘗試修正圖片路徑
                        String fixedUrl = fixImagePath(mdFilePath.getParent().toFile(), imageUrl);
                        if (fixedUrl != null) {
                            updatedContent.append(fixedUrl); // 插入修正後的路徑
                            contentModified = true; // 標記內容已變更
                            System.out.println("修正圖片路徑: " + fixedUrl);
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

                    System.out.println("檢查 HTML 圖片路徑: " + imageUrl);

                    // 檢查圖片是否存在
                    if (isImagePathValid(mdFilePath.getParent().toFile(), imageUrl)) {
                        finalContent.append(imageUrl); // 路徑正確，保持原路徑
                        System.out.println("HTML 圖片存在: " + imageUrl);
                    } else {
                        // 嘗試修正圖片路徑
                        String fixedUrl = fixImagePath(mdFilePath.getParent().toFile(), imageUrl);
                        if (fixedUrl != null) {
                            finalContent.append(fixedUrl); // 插入修正後的路徑
                            contentModified = true; // 標記內容已變更
                            System.out.println("修正 HTML 圖片路徑: " + fixedUrl);
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


    private class Tab3_manualRestoreRelativeURL extends Component{

    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MarkdownExtractorGUI().setVisible(true));
    }
}
