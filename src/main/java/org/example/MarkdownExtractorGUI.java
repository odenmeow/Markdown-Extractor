package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MarkdownExtractorGUI extends JFrame {

    // 定義兩個正則表達式：一個針對 Markdown 的 ![]()，另一個針對 HTML 的 <img src="">
    private static final String MARKDOWN_IMAGE_REGEX = "!\\[.*?\\]\\((.*?\\.(png|jpg|jpeg|gif|bmp|webp))\\)";
    private static final String HTML_IMAGE_REGEX = "<img\\s+[^>]*src=[\"'](.*?\\.(png|jpg|jpeg|gif|bmp|webp))[\"']";
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(MARKDOWN_IMAGE_REGEX, Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE_PATTERN = Pattern.compile(HTML_IMAGE_REGEX, Pattern.CASE_INSENSITIVE);
    private JButton processButton;
    private JTabbedPane tabbedPane;  // 新增的頁簽
    private Tab1_getImageFolder_MarkdownFile tab1; // 定義 tab1
    private Tab2_autoRestoreRelativeURL tab2; // 定義 tab2
    private Tab3_createOutlineTable tab3; // 定義 tab3
    private Tab4_imageCompressor tab4; // 定義 tab4

    public MarkdownExtractorGUI() {
        setTitle("Markdown Extractor");
        setSize(650, 450);
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

        // 初始化 tab4 類別並新增到頁簽
        tab4 = new Tab4_imageCompressor(this);

        tabbedPane.addTab("png_Images_Compressor",tab4.createFourthTab());

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
                case 3:
                    tab4.replaceAndGO();
                    break;
            }
        });

        add(processButton, BorderLayout.SOUTH);

        // 添加頁簽變更的監聽器
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 3) {
                // 如果選中的是 tab4，修改按鈕文字為 "Replace and GO"
                processButton.setText("Replace and GO");
            } else {
                // 否則恢復為 "Process Markdown"
                processButton.setText("Process Markdown");
            }
        });
    }

    // TransferHandler to handle file and folder drops
    private class FileDropHandler extends TransferHandler {
        private boolean isInput;  // true for input, false for output
        private DefaultListModel<String> ioFolderModel;

        private boolean alert;
        private String alertString;
        private Component passedInComponent;
        public FileDropHandler(boolean isInput, DefaultListModel<String> ioFolderModel, boolean alert, String alertString, Component passedInComponent) {
            this.isInput = isInput;
            this.ioFolderModel = ioFolderModel;
            this.alert = alert;
            this.alertString = alertString;
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
                // 獲取拖放的文件列表
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) {
                    // 如果是輸入圖片區域，允許添加資料夾、.md 文件和圖片文件
                    if (isInput) {
                        if ((file.isDirectory() || file.getName().endsWith(".md") || isImageFile(file))
                                && !ioFolderModel.contains(file.getAbsolutePath())) {
                            // 檢查是否已存在列表中，若不存在則添加
                            ioFolderModel.addElement(file.getAbsolutePath());
                        }
                    } else {
                        // 如果是輸出資料夾區域，則只允許添加資料夾
                        if (file.isDirectory() && !ioFolderModel.contains(file.getAbsolutePath())) {
                            if (ioFolderModel.size() > 0) {
                                ioFolderModel.clear();  // 清除現有資料夾，確保只有一個輸出資料夾
                            }

                            if (alert){

                                if (alertString.contains("集中圖片資料夾")) {

                                    JOptionPane.showMessageDialog(passedInComponent, alertString + file.getName());
                                }
                                else{
                                    JOptionPane.showMessageDialog(passedInComponent, alertString);
                                }
                            }
                            System.out.println("跳過");
                            ioFolderModel.addElement(file.getAbsolutePath());
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        // Helper method to determine if the file is an image
        private boolean isImageFile(File file) {
            String[] imageExtensions = {"png", "jpg", "jpeg", "gif", "bmp"};
            String fileName = file.getName().toLowerCase();
            for (String ext : imageExtensions) {
                if (fileName.endsWith(ext)) {
                    return true;
                }
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
            inputFolderList.setTransferHandler(new FileDropHandler(true,inputFolderModel, false, "", ancestorWindow));  // 支援拖放資料夾或文件
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
            outputFolderList.setTransferHandler(new FileDropHandler(false, outputFolderModel, false, "", ancestorWindow));  // 支援拖放資料夾
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
            inputFolderList.setTransferHandler(new FileDropHandler(true, inputFolderModel, false, "", ancestorWindow));  // 支援拖放資料夾或文件
            JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

            // Input Folder  >> 標題文字 :  按鈕  ， 下方為丟檔案進去的區塊
            inputPanelTitle.add(inputLabel, BorderLayout.WEST);
            inputPanel.add(inputPanelTitle, BorderLayout.NORTH);
            inputPanel.add(inputScrollPane, BorderLayout.CENTER);
            panelGridMain.add(inputPanel);

            JPanel panelGridR = new JPanel(new GridLayout(2, 1, 10, 10));
            panelGridR.setBorder(new EmptyBorder(0, 10, 0, 10));

            // Root Folder 右邊
            JPanel rootPanel = new JPanel(new BorderLayout());
            JLabel rootLabel = new JLabel("Root Folder: images & target's root");
            rootFolderModel = new DefaultListModel<>();
            rootFolderList = new JList<>(rootFolderModel);
            rootFolderList.setTransferHandler(new FileDropHandler(false, rootFolderModel, false,"", ancestorWindow));  // 支援拖放資料夾
            JScrollPane rootScrollPane = new JScrollPane(rootFolderList);  // 支援多個根目錄
            rootPanel.add(rootLabel, BorderLayout.NORTH);
            rootPanel.add(rootScrollPane, BorderLayout.CENTER);
            panelGridR.add(rootPanel);

            // Images Folder 右邊
            JPanel imagesPanel = new JPanel(new BorderLayout());
            JLabel imagesLabel = new JLabel("Images Folder:");
            imagesFolderModel = new DefaultListModel<>();
            imagesFolderList = new JList<>(imagesFolderModel);
            imagesFolderList.setTransferHandler(new FileDropHandler(false, imagesFolderModel, true, ".md 圖片 url 的集中圖片資料夾名稱\n" +
                    "例: ../../Images 中\n"+ "Images將替換為:\n", ancestorWindow));  // 支援拖放資料夾
            JScrollPane imagesScrollPane = new JScrollPane(imagesFolderList);  // 支援多個圖片資料夾
            imagesPanel.add(imagesLabel, BorderLayout.NORTH);
            imagesPanel.add(imagesScrollPane, BorderLayout.CENTER);
            panelGridR.add(imagesPanel);

            panelGridMain.add(panelGridR, BorderLayout.EAST);

            myBasicPanel.add(panelGridMain, BorderLayout.NORTH);

            // Failed files list (with scroll bar) 下方 (p.s. 視窗最下方還有 processMarkdown按鈕)
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
            inputFolderList.setTransferHandler(new FileDropHandler(true, inputFolderModel, false, "", ancestorWindow));  // 支援拖放資料夾或文件
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


    private class Tab4_imageCompressor extends Component {
        private DefaultListModel<String> inputImageModel;  // 支援拖曳輸入圖片
        private JList<String> inputImageList;

        private DefaultListModel<String> outputListModel = new DefaultListModel<>() ;  // 輸出資料夾的模型
        private JList<String> outputFolderList = new JList<>(outputListModel);;  // 輸出資料夾列表
        private JTextField compressionLevelField;  // 壓縮級別
        private DefaultListModel<String> outputModel = new DefaultListModel<>() ;  // 顯示壓縮後的圖片
        private JList<String> outputList;  // 顯示輸出圖片列表
        private JFrame ancestorWindow;
        private JTextField qualityField;
        private Map<String, List<String>> processedImagesMap;  // 儲存每個 .md 文件對應成功處理的圖片 URL
        private JCheckBox onlyTodayCheckBox;  // 新增的 "Only Today" 勾選框
        private static final String CONFIG_FILE = "config.properties";  // 保存設定的文件名
        private String defaultGitRootPath;  // 保存預設 Git 根目錄
        private String defaultImgFolderPath;  // 保存預設 Central Img Folder 路徑
        private JCheckBox centralImgFolderSaveAsDefaultBox;

        // 載入 config.properties 文件
        private void loadConfig() {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
                defaultGitRootPath = props.getProperty("gitRootPath", ""); // 默認為空字符串
                defaultImgFolderPath = props.getProperty("imgFolderPath", ""); // 默認為空字符串

                // 如果 imgFolderPath 有值，將其設置到 outputListModel 中
                if (!defaultImgFolderPath.isEmpty()) {
                    outputListModel.addElement(defaultImgFolderPath);
                    System.out.println("已添加");
                    outputFolderList.revalidate();
                    outputFolderList.repaint();
                }
            } catch (IOException e) {
                System.out.println("Configuration file not found, using defaults.");
            }
        }


        // 保存設定到 config.properties 文件
        private void saveConfig(String key, String value) {
            Properties props = new Properties();
            // 讀取現有的配置
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                // 文件不存在時，創建一個新的 properties
                System.out.println("Configuration file not found, creating new one.");
            }
            // 更新或添加新的設定項
            props.setProperty(key, value);
            // 保存所有配置
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, "User Configuration");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public Tab4_imageCompressor(JFrame ancestorWindow) {
            this.ancestorWindow = ancestorWindow;
            this.processedImagesMap = new HashMap<>();
            loadConfig(); // 載入設定
        }

        private Component createFourthTab() {

            //               < GridLayout 只能三等份，不能按ˋ比例設計 >
            // 使用 GridLayout 才能均分，同步壓縮空間 ，否則 BorderLayout 會有不同組合之反應。
            // 例如 North + CENTER + south 會導致 CENTER 優先被壓縮。  N +S 會導致 S 優先被壓縮。
            // JPanel myBasicPanel = new JPanel(new GridLayout(3, 1, 10, 10));
            // myBasicPanel.setBorder(new EmptyBorder(10,10,0,10));
            //              < /GridLayout 只能三等份，不能按ˋ比例設計 >


            //              < GridBagLayout ，可以自己設定百分比 >
            JPanel myBasicPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();

            //            // 設定第一個元件 (占 40% 垂直方向空間)
            //            gbc.gridx = 0;       // 設置列位置，只有一列所以是0
            //            gbc.gridy = 0;       // 設置行位置，第一行
            //            gbc.weightx = 1.0;   // 水平方向佔滿（因為只有一列，佔比為 100%）
            //            gbc.weighty = 0.4;   // 垂直方向佔比 40%
            //            gbc.fill = GridBagConstraints.BOTH;  // 填滿水平方向和垂直方向
            //            myBasicPanel.add(component1, gbc);
            //
            //            // 設定第二個元件 (占 20% 垂直方向空間)
            //            gbc.gridy = 1;       // 設置行位置，第二行
            //            gbc.weighty = 0.2;   // 垂直方向佔比 20%
            //            myBasicPanel.add(component2, gbc);
            //
            //            // 設定第三個元件 (占 40% 垂直方向空間)
            //            gbc.gridy = 2;       // 設置行位置，第三行
            //            gbc.weighty = 0.4;   // 垂直方向佔比 40%
            //            myBasicPanel.add(component3, gbc);
            //              < /GridBagLayout ，可以自己設定百分比 >






            //              <Input markdownFiles + OnlyToday + Clear + scrollPane>
            // 上方輸入圖片區域
            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("<html><font color='blue'>Input markdownFiles</font>" +
                    "<font color='red'>  (not folder)</font>"+
                    "<font color='blue'> : </font>"+
                    "</html>");
            inputImageModel = new DefaultListModel<>();
            inputImageList = new JList<>(inputImageModel);

            // 創建輸入圖片列表
            inputImageModel = new DefaultListModel<>();
            inputImageList = new JList<>(inputImageModel);

            // 添加雙擊監聽器以打開選中的檔案
            inputImageList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { // 檢測雙擊事件
                        int index = inputImageList.locationToIndex(e.getPoint()); // 獲取被雙擊項目的索引
                        if (index != -1) {
                            String selectedFilePath = inputImageModel.getElementAt(index);
                            File file = new File(selectedFilePath);
                            if (file.exists() && file.isFile()) {
                                try {
                                    // 使用 Desktop 類打開文件
                                    Desktop.getDesktop().open(file);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to open the file: " + file.getAbsolutePath());
                                }
                            } else {
                                JOptionPane.showMessageDialog(ancestorWindow, "The selected item is not a valid file.");
                            }
                        }
                    }
                }
            });

            inputImageList.setTransferHandler(new FileDropHandler(true, inputImageModel, false, "", ancestorWindow));  // 支援拖放圖片
            JScrollPane inputScrollPane = new JScrollPane(inputImageList);

            // 添加清空按鈕和 Only Today 勾選框
            onlyTodayCheckBox = new JCheckBox("Only Today", true);
            JButton clearButton = new JButton("Clear Input & Output");
            clearButton.addActionListener(e -> {
                inputImageModel.clear();  // 清空輸入圖片列表
                outputListModel.clear();  // 清空輸出資料夾列表 先放置，呈現為一列。
                outputModel.clear(); // images 的輸出顯示區 多列
                JOptionPane.showMessageDialog(ancestorWindow, "Input images and output folder cleared.");
            });

            JPanel inputPanelTitle = new JPanel(new BorderLayout());
            inputPanelTitle.add(inputLabel, BorderLayout.WEST);
            inputPanelTitle.add(onlyTodayCheckBox, BorderLayout.CENTER);
            inputPanelTitle.add(clearButton, BorderLayout.EAST);

            inputPanel.add(inputPanelTitle, BorderLayout.NORTH);
            inputPanel.add(inputScrollPane, BorderLayout.CENTER);
            // myBasicPanel.add(inputPanel, BorderLayout.NORTH);

            gbc.gridx = 0;       // 設置列位置，只有一列所以是0
            gbc.gridy = 0;       // 設置行位置，第一行
            gbc.weightx = 1.0;   // 水平方向佔滿（因為只有一列，佔比為 100%）
            gbc.weighty = 0.4;   // 垂直方向佔比 40%
            gbc.fill = GridBagConstraints.BOTH;  // 填滿水平方向和垂直方向
            gbc.insets = new Insets(3, 10, 3, 5); // 添加內邊距 (上, 左, 下, 右)
            myBasicPanel.add(inputPanel, gbc);


            //              </Input markdownFiles + OnlyToday + Clear + scrollPane>






            // 下方輸出設定區域
            JPanel settingZonePanel = new JPanel(new GridBagLayout());
            GridBagConstraints settingZoneGbc = new GridBagConstraints();
            // 上半部分：輸出資料夾選擇區域

            JPanel centralImgFolderPanel = new JPanel(new GridBagLayout());
            // 1,2
            // 3,4   ， GridBagLayout，希望 1 = Central Img Folder ，2=save as default
            // 3+4 跨行 合併後，內容 outputFolderScrollPane 置中

            // 無須再創造，可以沿用上面的 gbc (當作儲存參數的物件能重用)，但避免 跟別人互相 cover 因此又創建
            GridBagConstraints imgGbc = new GridBagConstraints();
            JLabel centralImgText = new JLabel("Central Img Folder:");
            centralImgFolderSaveAsDefaultBox = new JCheckBox("save", true);

            // 資料夾列表，支持拖放功能，使用 JList 作為資料夾項目展示，限制高度

            outputFolderList.setTransferHandler(new FileDropHandler(false, outputListModel, false, "", ancestorWindow));  // 支援拖放資料夾
            // 添加雙擊監聽器以打開選中的資料夾
            outputFolderList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { // 檢測雙擊事件
                        int index = outputFolderList.locationToIndex(e.getPoint()); // 獲取被雙擊項目的索引
                        if (index != -1) {
                            String selectedFolderPath = outputListModel.getElementAt(index); // 從 outputListModel 中獲取資料夾路徑
                            File folder = new File(selectedFolderPath);
                            if (folder.exists() && folder.isDirectory()) {
                                try {
                                    // 使用 Desktop 類打開資料夾
                                    Desktop.getDesktop().open(folder);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to open the folder: " + folder.getAbsolutePath());
                                }
                            } else {
                                JOptionPane.showMessageDialog(ancestorWindow, "The selected item is not a valid directory.");
                            }
                        }
                    }
                }
            });

            // 當 outputFolderList 有變化時保存到配置文件，使用這個才可以每次都保存 而不是有變化才保存 。例如 addListSelectionListener ( 第一次丟入資料夾 不算是有變化 )  。
            outputListModel.addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent e) {
                    saveIfNecessary();
                }

                @Override
                public void intervalRemoved(ListDataEvent e) {
                    saveIfNecessary();
                }

                @Override
                public void contentsChanged(ListDataEvent e) {
                    saveIfNecessary();
                }

                private void saveIfNecessary() {
                    // 只有當勾選框被勾選且列表不為空的情況下，才保存配置
                    if (!outputListModel.isEmpty() && centralImgFolderSaveAsDefaultBox.isSelected()) {
                        String selectedFolderPath = outputListModel.getElementAt(0);
                        saveConfig("imgFolderPath", selectedFolderPath);
                        System.out.println("已經保存了");
                    }
                }
            });

            // 限制 JList 的顯示行數和大小
            outputFolderList.setVisibleRowCount(1); // 設定可見行數，保持高度一致
            JScrollPane outputFolderScrollPane = new JScrollPane(outputFolderList);
            outputFolderScrollPane.setPreferredSize(new Dimension(500, 40)); // 設置首選尺寸來限制高度和寬度
            outputFolderScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);  // 不顯示水平捲軸
            outputFolderScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);  // 不顯示垂直捲軸


            // 設定元件 1 Central Img Folder 文字部分 (30% 垂直方向空間)
            imgGbc.gridx = 0;       // col 0
            imgGbc.gridy = 0;       // row 0
            imgGbc.weightx = 1.0;   // 水平方向佔滿
            imgGbc.weighty = 0.3;   // 垂直方向佔比 30%
            imgGbc.gridwidth = 1;   // 不合併列
            imgGbc.gridheight = 1;  // 不合併行
            imgGbc.fill = GridBagConstraints.BOTH;
            // 下面是為了避免 Central 的文字 沒有對齊，把 5 改 0 就能理解。
            imgGbc.insets = new Insets(0, 5, 0, 0); // 添加內邊距 (上, 左, 下, 右)
            centralImgFolderPanel.add(centralImgText, imgGbc);

            // 設定元件 2 saveAsDefault 部分 (30% 垂直方向空間)
            imgGbc.gridx = 0;       // col 0
            imgGbc.gridy = 1;       // row 1
            imgGbc.weightx = 1.0;   // 水平方向佔滿
            imgGbc.weighty = 0.3;   // 垂直方向佔比 30%
            imgGbc.gridwidth = 1;   // 不合併列
            imgGbc.gridheight = 1;  // 不合併行
            imgGbc.fill = GridBagConstraints.BOTH;
            imgGbc.insets = new Insets(0, 0, 0, 0); // 添加內邊距 (上, 左, 下, 右)

            // 增加讀取的功能
            JButton centralImgFolderReadBtn = new JButton("read");
            centralImgFolderReadBtn.addActionListener((event) -> {
                loadConfig(); // 重新讀取配置文件
                if (!defaultImgFolderPath.isEmpty()) {
                    outputListModel.clear();
                    outputListModel.addElement(defaultImgFolderPath);
                    outputFolderList.revalidate();
                    outputFolderList.repaint();
                }
                JOptionPane.showMessageDialog(ancestorWindow, "Successfully loaded default image folder path.");
            });
            JPanel saveAndReadBlock = new JPanel(new GridBagLayout());
            GridBagConstraints saveAndReadGbc = new GridBagConstraints();
            saveAndReadGbc.gridx = 0;
            saveAndReadGbc.gridy = 0;
            saveAndReadBlock.add(centralImgFolderSaveAsDefaultBox, saveAndReadGbc);
            saveAndReadGbc.gridx = 1;
            saveAndReadBlock.add(centralImgFolderReadBtn, saveAndReadGbc);
            centralImgFolderPanel.add(saveAndReadBlock, imgGbc);

            // 設定元件 outputFolderScrollPane 放置於右側 (合併行 0 和 1)
            imgGbc.gridx = 1;           // col 1
            imgGbc.gridy = 0;           // row 0
            imgGbc.weightx = 1.0;       // 水平方向佔滿
            imgGbc.weighty = 0.4;       // 垂直方向佔比 40% （兩行加起來的比例，總共是60%）
            imgGbc.gridwidth = 1;       // 不合併列
            imgGbc.gridheight = 2;      // 合併行 0 和行 1
            imgGbc.fill = GridBagConstraints.BOTH;  // 填滿水平方向和垂直方向
            imgGbc.anchor = GridBagConstraints.CENTER;  // 確保內容置中顯示
            imgGbc.insets = new Insets(0, 5, 0, 0); // 添加內邊距 (上, 左, 下, 右)
            centralImgFolderPanel.add(outputFolderScrollPane, imgGbc);







            // 添加上半部分到 settingZonePanel 的上方
            settingZoneGbc.gridx = 0;
            settingZoneGbc.gridy = 0;
            settingZoneGbc.fill = GridBagConstraints.BOTH;
            settingZonePanel.add(centralImgFolderPanel, settingZoneGbc);

            // 下半部分：壓縮級別和處理按鈕
            JPanel compressionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel compressionLabel = new JLabel("Compression Level(0-6best):");
            compressionLevelField = new JTextField("5", 5);  // 預設壓縮級別為 9
            JButton processButton = new JButton("Process Images");

            // 追加 webp 所使用的參數
            JLabel qualityLabel = new JLabel("Quality(0-100best):");
            qualityField = new JTextField("80", 5);  // 預設品質為 80
            compressionPanel.add(qualityLabel);
            compressionPanel.add(qualityField);
            compressionPanel.add(compressionLabel);
            compressionPanel.add(compressionLevelField);
            compressionPanel.add(processButton);

            // 添加下半部分到 settingZonePanel 的中間
            settingZoneGbc.gridx = 0;
            settingZoneGbc.gridy = 1;
            settingZoneGbc.anchor = GridBagConstraints.WEST;
            settingZoneGbc.fill = GridBagConstraints.BOTH;

            settingZonePanel.add(compressionPanel, settingZoneGbc);

            // 監聽處理按鈕
            processButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    processImages();
                }
            });

            // 輸出圖片顯示區
            outputList = new JList<>(outputModel);
            JScrollPane outputScrollPane = new JScrollPane(outputList);
            // 添加雙擊監聽器以打開資料夾
            outputList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { // 檢測雙擊事件
                        int index = outputList.locationToIndex(e.getPoint()); // 獲取被雙擊項目的索引
                        if (index != -1) {
                            String selectedFolderPath = outputListModel.getElementAt(index);
                            File folder = new File(selectedFolderPath);
                            if (folder.exists() && folder.isDirectory()) {
                                try {
                                    // 使用 Desktop 類打開文件管理器並顯示該資料夾
                                    Desktop.getDesktop().open(folder);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to open the folder: " + folder.getAbsolutePath());
                                }
                            } else {
                                JOptionPane.showMessageDialog(ancestorWindow, "The selected item is not a valid folder.");
                            }
                        }
                    }
                }
            });

            // myBasicPanel.add(settingZonePanel,BorderLayout.CENTER);
            // myBasicPanel.add(outputScrollPane, BorderLayout.SOUTH);
            // 設定第二個元件 (占 20% 垂直方向空間)
            gbc.gridy = 1;       // 設置行位置，第二行
            gbc.weighty = 0.2;   // 垂直方向佔比 20%
            // settingZonePanel.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
            gbc.insets = new Insets(0, 0, 0, 0); // 添加內邊距 (上, 左, 下, 右)
            myBasicPanel.add(settingZonePanel, gbc);

            // 設定第三個元件 (占 40% 垂直方向空間)
            gbc.gridy = 2;       // 設置行位置，第三行
            gbc.weighty = 0.4;   // 垂直方向佔比 40%
            gbc.insets = new Insets(0, 10, 3, 5); // 添加內邊距 (上, 左, 下, 右)
            myBasicPanel.add(outputScrollPane, gbc);




            // 追加功能            < ---------------------Add md Files By Git ------------------------>
            // 添加 addByGit 按鈕
            JButton addByGitButton = new JButton("Add By Git");
            addByGitButton.addActionListener(e -> {
                JTextField gitRootPathField = new JTextField(30);
                gitRootPathField.setText(defaultGitRootPath);

                JCheckBox saveCheckBox = new JCheckBox("Save this path as default");
                saveCheckBox.setSelected(true);  // 默認選中

                JPanel panel = new JPanel(new BorderLayout(5, 5));
                panel.add(new JLabel("Enter the path to the top-level folder containing .git:"), BorderLayout.NORTH);
                panel.add(gitRootPathField, BorderLayout.CENTER);
                panel.add(saveCheckBox, BorderLayout.SOUTH);

                int result = JOptionPane.showConfirmDialog(ancestorWindow, panel, "Add By Git",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    String gitRootPath = gitRootPathField.getText();
                    if (gitRootPath != null && !gitRootPath.trim().isEmpty()) {
                        File gitRoot = new File(gitRootPath);
                        if (gitRoot.exists() && gitRoot.isDirectory()) {
                            // 如果選中保存，則保存 Git 路徑
                            if (saveCheckBox.isSelected()) {
                                saveConfig("gitRootPath",gitRootPath);
                                defaultGitRootPath = gitRootPath;  // 更新當前的 Git 路徑
                            }

                            try {
                                // 使用 git 命令查詢今日有異動的 .md 文件
                                ProcessBuilder builder = new ProcessBuilder("git", "-C", gitRootPath, "diff", "--name-only", "--diff-filter=AM", "--since=midnight");
                                builder.directory(gitRoot);
                                Process process = builder.start();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                boolean foundAnyMdFile = false;

                                while ((line = reader.readLine()) != null) {
                                    if (line.endsWith(".md")) {
                                        File mdFile = new File(gitRoot, line);
                                        if (mdFile.exists()) {
                                            // 檢查是否已經存在於 inputImageModel 中
                                            if (!inputImageModel.contains(mdFile.getAbsolutePath())) {
                                                inputImageModel.addElement(mdFile.getAbsolutePath());
                                                foundAnyMdFile = true;
                                            }
                                        }
                                    }
                                }

                                process.waitFor();

                                // 如果沒有找到任何 .md 文件，顯示提示信息
                                if (!foundAnyMdFile) {
                                    JOptionPane.showMessageDialog(ancestorWindow, "No modified .md files found for today.");
                                }

                                // 使用 git status --short 命令查詢未追蹤的 .md 文件
                                ProcessBuilder untrackedBuilder = new ProcessBuilder("git", "-C", gitRootPath, "ls-files", "--others", "--exclude-standard", "*.md");
                                untrackedBuilder.directory(gitRoot);
                                Process untrackedProcess = untrackedBuilder.start();
                                BufferedReader untrackedReader = new BufferedReader(new InputStreamReader(untrackedProcess.getInputStream()));
                                String untrackedLine;

                                while ((untrackedLine = untrackedReader.readLine()) != null) {
                                    File mdFile = new File(gitRoot, untrackedLine);
                                    if (mdFile.exists() && !inputImageModel.contains(mdFile.getAbsolutePath())) {
                                        inputImageModel.addElement(mdFile.getAbsolutePath());
                                        foundAnyMdFile = true;
                                    }
                                }

                                untrackedProcess.waitFor();

                                // 如果還是沒有找到任何 .md 文件，顯示提示信息
                                if (!foundAnyMdFile) {
                                    JOptionPane.showMessageDialog(ancestorWindow, "No modified or untracked .md files found.");
                                }

                            } catch (IOException | InterruptedException ex) {
                                ex.printStackTrace();
                                JOptionPane.showMessageDialog(ancestorWindow, "Failed to fetch modified or untracked .md files from git repository.");
                            }
                        } else {
                            JOptionPane.showMessageDialog(ancestorWindow, "Invalid path or directory does not exist.");
                        }
                    }
                }
            });

            // 修改 inputPanelTitle 中的按鈕位置
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(addByGitButton);
            buttonPanel.add(clearButton);
            inputPanelTitle.add(buttonPanel, BorderLayout.EAST);

            return myBasicPanel;
        }

        private void processImages() {
            if (inputImageModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please provide input markdown files.");
                return;
            }

            if (outputListModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please provide an output folder.");
                return;
            }

            String outputFolder = outputListModel.getElementAt(0);
            File outputFolderFile = new File(outputFolder);
            String centralImgFolderGOODAncestor = outputFolderFile.getParent();

            // 檢查所有 .md 文件是否包含 centralImgFolderGOODAncestor
            for (int i = 0; i < inputImageModel.size(); i++) {
                String inputPath = inputImageModel.getElementAt(i);
                File inputFile = new File(inputPath);

                try {
                    if (!inputFile.getCanonicalPath().contains(centralImgFolderGOODAncestor)) {
                        JOptionPane.showMessageDialog(ancestorWindow, "請提供所有 .md 檔案共同的 centralImgFolder，且其位於相同的上層資料夾。");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to access the markdown file: " + inputPath);
                    return;
                }
            }

            // 清空臨時資料夾
            File toBeClearTempProcessFolder = new File(outputFolder, "tempProcessFolder");
            File toBeClearTempMdFolder = new File(outputFolder, "temp_md_files");
            try {
                if (toBeClearTempProcessFolder.exists()) {
                    deleteDirectoryRecursively(toBeClearTempProcessFolder.toPath());
                }
                if (toBeClearTempMdFolder.exists()) {
                    deleteDirectoryRecursively(toBeClearTempMdFolder.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "Failed to clean temporary process folders.");
                return;
            }

            int compressionLevel;
            int quality;

            try {
                compressionLevel = Integer.parseInt(compressionLevelField.getText());
                quality = Integer.parseInt(qualityField.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please enter valid values for compression level and quality.");
                return;
            }

            String todayDate = LocalDate.now().toString();
            List<String> noNeedCompressMarkDownFiles = new ArrayList<>();
            List<String> failedFiles = new ArrayList<>();
            List<String> processedFiles = new ArrayList<>();

            for (int i = 0; i < inputImageModel.size(); i++) {
                String inputPath = inputImageModel.getElementAt(i);
                File inputFile = new File(inputPath);

                if (inputFile.getName().endsWith(".md")) {
                    try {
                        String content = new String(Files.readAllBytes(inputFile.toPath()));
                        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
                        StringBuilder updatedContent = new StringBuilder();
                        int lastIndex = 0;
                        List<String> eachMarkdownOldImageUrls = new ArrayList<>();

                        while (markdownMatcher.find()) {
                            updatedContent.append(content, lastIndex, markdownMatcher.start(1));
                            String imageUrl = markdownMatcher.group(1);

                            if (imageUrl.endsWith(".png")) {
                                File imageFile = new File(inputFile.getParent(), imageUrl);
                                if (imageFile.exists()) {
                                    // 壓縮圖片
                                    if (onlyTodayCheckBox.isSelected() && imageUrl.contains(todayDate)) {
                                        compressImageWebp(imageFile.getAbsolutePath(), outputFolder, compressionLevel, quality);
                                        String newImageUrl = imageUrl.replace(".png", ".webp");
                                        updatedContent.append(newImageUrl);
                                        eachMarkdownOldImageUrls.add(imageFile.getAbsolutePath());
                                    } else if (!onlyTodayCheckBox.isSelected()) {
                                        compressImageWebp(imageFile.getAbsolutePath(), outputFolder, compressionLevel, quality);
                                        String newImageUrl = imageUrl.replace(".png", ".webp");
                                        updatedContent.append(newImageUrl);
                                        eachMarkdownOldImageUrls.add(imageFile.getAbsolutePath());
                                    } else {
                                        updatedContent.append(imageUrl);
                                    }
                                } else {
                                    System.out.println("圖片不存在：" + imageFile.getAbsolutePath());
                                    updatedContent.append(imageUrl);
                                }
                            } else {
                                updatedContent.append(imageUrl);
                            }

                            lastIndex = markdownMatcher.end(1);
                        }
                        updatedContent.append(content.substring(lastIndex));

                        processedImagesMap.put(inputFile.getName(), eachMarkdownOldImageUrls);

                        if (!eachMarkdownOldImageUrls.isEmpty()) {
                            if (!outputModel.contains(inputFile.getName())) {
                                outputModel.addElement(inputFile.getName());
                            }
                            File tempMdFolder = new File(outputFolder, "temp_md_files");
                            if (!tempMdFolder.exists()) {
                                tempMdFolder.mkdirs();
                            }

                            File tempMdOutput = new File(tempMdFolder, inputFile.getName());
                            try (BufferedWriter writer = Files.newBufferedWriter(tempMdOutput.toPath())) {
                                writer.write(updatedContent.toString());
                            }
                            processedFiles.add(inputFile.getName());
                        } else {
                            noNeedCompressMarkDownFiles.add(inputFile.getName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        failedFiles.add(inputFile.getName());
                    }
                }
            }

            // 集中顯示處理完成的訊息
            StringBuilder completeMsg = new StringBuilder();
            completeMsg.append("Markdown processing completed.\n");

            if (!processedFiles.isEmpty()) {
                completeMsg.append("\n已成功處理的文件:\n");
                processedFiles.forEach(fileName -> completeMsg.append(fileName).append("\n"));
            }

            if (!noNeedCompressMarkDownFiles.isEmpty()) {
                completeMsg.append("\n以下文件無任何 PNG 需壓縮或 URL 不符合今日圖片，已自動剔除:\n");
                noNeedCompressMarkDownFiles.forEach(fileName -> completeMsg.append(fileName).append("\n"));
            }

            if (!failedFiles.isEmpty()) {
                completeMsg.append("\n以下文件處理失敗:\n");
                failedFiles.forEach(fileName -> completeMsg.append(fileName).append("\n"));
            }

            JOptionPane.showMessageDialog(ancestorWindow, completeMsg.toString());
        }






        //        private void compressImagePNG(String imagePath, String outputFolder, int compressionLevel) {
//            // 創建臨時文件夾
//            File tempProcessFolder = new File(outputFolder, "tempProcessFolder");
//            if (!tempProcessFolder.exists()) {
//                tempProcessFolder.mkdirs();
//            }
//
//            // 在臨時文件夾中存放壓縮後的文件
//            String tempOutputFilePath = tempProcessFolder.getAbsolutePath() + File.separator + new File(imagePath).getName();
//
//            // 刪除既有的臨時檔案（如果存在）
//            File tempOutputFile = new File(tempOutputFilePath);
//            if (tempOutputFile.exists()) {
//                tempOutputFile.delete();
//            }
//
//            // 使用 ffmpeg 進行壓縮
//            String command = String.format("ffmpeg -y -i \"%s\" -compression_level %d \"%s\"", imagePath, compressionLevel, tempOutputFilePath);
//
//            try {
//                Process process = Runtime.getRuntime().exec(command);
//                process.waitFor();
//                // 壓縮後的圖片添加到輸出列表
//                outputModel.addElement(tempOutputFilePath);
//            } catch (Exception e) {
//                e.printStackTrace();
//                JOptionPane.showMessageDialog(ancestorWindow, "Failed to compress image: " + imagePath);
//            }
//        }
        private void compressImageWebp(String imagePath, String outputFolder, int compressionLevel, int quality) {
            File tempProcessFolder = new File(outputFolder, "tempProcessFolder");
            if (!tempProcessFolder.exists()) {
                tempProcessFolder.mkdirs();
            }

            String tempOutputFilePath = tempProcessFolder.getAbsolutePath() + File.separator + new File(imagePath).getName().replace(".png", ".webp");
            File tempOutputFile = new File(tempOutputFilePath);
            if (tempOutputFile.exists()) {
                tempOutputFile.delete();
            }

            String command = String.format("ffmpeg -y -i \"%s\" -compression_level %d -q:v %d \"%s\"", imagePath, compressionLevel, quality, tempOutputFilePath);

            try {
                Process process = Runtime.getRuntime().exec(command);
                try (var inputStream = process.getInputStream();
                     var errorStream = process.getErrorStream()) {
                    process.waitFor();
                    // 顯示的是 被轉換的 png 之 url，因為已經在 processImage 那邊有寫加入 .md 檔名就好，比較容易知道誰有被處理。
                    // outputModel.addElement(tempOutputFilePath);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(ancestorWindow, "Failed to compress image: " + imagePath);
            }
        }




        public void replaceAndGO() {
            if (outputListModel.isEmpty()) {
                JOptionPane.showMessageDialog(ancestorWindow, "Please provide an output folder.");
                return;
            }

            // 獲取輸出的資料夾（假設只選擇一個輸出資料夾）
            String outputFolder = outputListModel.getElementAt(0);
            File tempProcessFolder = new File(outputFolder, "tempProcessFolder");
            File tempMdFolder = new File(outputFolder, "temp_md_files");

            if (!tempProcessFolder.exists() || tempProcessFolder.listFiles() == null) {
                JOptionPane.showMessageDialog(ancestorWindow, "No processed files found to replace.");
                return;
            }

            // 創建垃圾桶集中資料夾
            File trashFolder = new File(outputFolder, "Trash_Backup");
            File trashMdFolder = new File(trashFolder, "md_files");
            File trashPngFolder = new File(trashFolder, "png_files");
            if (!trashMdFolder.exists()) {
                trashMdFolder.mkdirs();
            }
            if (!trashPngFolder.exists()) {
                trashPngFolder.mkdirs();
            }

            // 設置 Trash_Backup 資料夾的權限，確保用戶可以刪除
            setFolderPermissions(trashFolder);
            setFolderPermissions(trashMdFolder);
            setFolderPermissions(trashPngFolder);


            // 替換壓縮後的圖片
            for (File tempFile : tempProcessFolder.listFiles()) {
                File finalOutputFile = new File(outputFolder, tempFile.getName());
                if (finalOutputFile.exists()) {
                    // 備份原始文件到垃圾桶中的 png 集中資料夾
                    try {
                        File backupFile = new File(trashPngFolder, finalOutputFile.getName());
                        Files.copy(finalOutputFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(ancestorWindow, "Failed to backup file to trash: " + finalOutputFile.getAbsolutePath());
                        return;
                    }
                    finalOutputFile.delete(); // 刪除既有的輸出檔案，確保覆蓋
                }
                try {
                    Files.move(tempFile.toPath(), finalOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to move file: " + tempFile.getAbsolutePath());
                }
            }

            // 替換 .md 文件（應替換原始的 .md 文件）
            if (tempMdFolder.exists() && tempMdFolder.listFiles() != null) {
                for (File tempMdFile : tempMdFolder.listFiles()) {
                    // 從 inputImageModel 中找到對應的原始 .md 文件
                    String originalMdFilePath = findOriginalMdFile(tempMdFile.getName());
                    if (originalMdFilePath == null) {
                        JOptionPane.showMessageDialog(ancestorWindow, "Original markdown file not found for: " + tempMdFile.getName());
                        continue;
                    }
                    File originalMdFile = new File(originalMdFilePath);
                    try {
                        // 備份原始的 .md 文件到垃圾桶中的 md 資料夾
                        File backupMdFile = new File(trashMdFolder, originalMdFile.getName());
                        Files.copy(originalMdFile.toPath(), backupMdFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        // 用臨時處理的 .md 文件替換原始文件
                        Files.copy(tempMdFile.toPath(), originalMdFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        // 使用 lambda 表達式備份並刪除對應的原始 .png 圖片
                        List<String> oldImageUrls = processedImagesMap.get(originalMdFile.getName());
                        if (oldImageUrls != null) {
                            oldImageUrls.forEach(url -> {
                                File pngFile = new File(url);
                                if (pngFile.exists()) {
                                    try {
                                        // 備份 .png 文件到垃圾桶中的 png 資料夾
                                        File backupPngFile = new File(trashPngFolder, pngFile.getName());
                                        Files.copy(pngFile.toPath(), backupPngFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                        boolean deleted = pngFile.delete();
                                        if (deleted) {
                                            System.out.println("已刪除原始圖片: " + url);
                                        } else {
                                            System.err.println("無法刪除圖片: " + url);
                                            JOptionPane.showMessageDialog(ancestorWindow, "無法刪除圖片: " + url);
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        JOptionPane.showMessageDialog(ancestorWindow, "Failed to backup .png file: " + pngFile.getAbsolutePath());
                                    }
                                } else {
                                    System.err.println("找不到圖片: " + url);
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(ancestorWindow, "Failed to replace markdown file: " + originalMdFile.getName());
                    }
                }
            }

                // 刪除臨時資料夾
                try {
                    // img 資料夾
                    if (tempProcessFolder.exists()) {
                        deleteDirectoryRecursively(tempProcessFolder.toPath());
                    }
                    // .md 資料夾
                    if (tempMdFolder.exists()) {
                        deleteDirectoryRecursively(tempMdFolder.toPath());
                    }

                    // 嘗試將 Trash_Backup 資料夾移動到回收桶
                    if (trashFolder.exists()) {
                        moveToTrash(trashFolder);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ancestorWindow, "Failed to delete temporary process folder.");
                }
            }

        // 將文件或文件夾移動到資源回收桶，並在失敗時提示用戶
        private void moveToTrash(File file) {
            if (file.exists()) {
                boolean success = false;
                int retryCount = 3; // 嘗試3次
                for (int i = 0; i < retryCount; i++) {
                    success = Desktop.getDesktop().moveToTrash(file);
                    if (success) {
                        // 移動成功後，顯示成功訊息並結束嘗試
                        JOptionPane.showMessageDialog(ancestorWindow, "替換成功、已將舊資料移到回收桶 :: Replaced. Old files recycled " + file.getAbsolutePath());
                        System.out.println("替換成功、已將舊資料移到回收桶 :: Replaced. Old files recycled " + file.getAbsolutePath());
                        break;
                    }
                    // 等待0.5秒再重試
                    try {
                        Thread.sleep(500); // 等待 0.5 秒
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                // 如果在所有嘗試後仍然無法移動，顯示錯誤訊息
                if (!success) {
                    JOptionPane.showMessageDialog(ancestorWindow, "丟垃圾失敗 :: Failed to move folder to recycle bin: " + file.getAbsolutePath());
                    System.out.println("丟垃圾失敗 :: Failed to move folder to recycle bin: " + file.getAbsolutePath());
                    System.out.println("如果不需trashFolder內容，建議手動使用管理者權限，cmd 輸入下面可以強制永久刪除");
                    System.out.println("Remove-Item -Path \"" + file.getAbsolutePath() + "\" -Recurse -Force");
                }
            }
        }



        // 設置文件夾的權限，確保用戶可以刪除
        private void setFolderPermissions(File folder) {
            if (folder.exists()) {
                folder.setWritable(true, false);
                folder.setReadable(true, false);
                folder.setExecutable(true, false);

                System.out.println("已嘗試設置權限: " + folder.getAbsolutePath() + " (權限設置可能不受支持，忽略錯誤)");
            }
        }




        // 用於查找原始 .md 文件的路徑
        private String findOriginalMdFile(String fileName) {
            for (int i = 0; i < inputImageModel.size(); i++) {
                String inputPath = inputImageModel.getElementAt(i);
                File inputFile = new File(inputPath);
                if (inputFile.getName().equals(fileName) && inputFile.getName().endsWith(".md")) {
                    return inputFile.getAbsolutePath();
                }
            }
            return null; // 如果找不到對應的原始文件
        }

        // 新增一個方法用來遞歸刪除資料夾
        private void deleteDirectoryRecursively(Path directory) throws IOException {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder()) // 確保先刪除子文件再刪除父資料夾
                    .map(Path::toFile)
                    .forEach(File::delete);
        }


    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MarkdownExtractorGUI().setVisible(true));
    }
}
