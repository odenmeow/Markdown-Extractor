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

    private DefaultListModel<String> inputFolderModel;
    private JList<String> inputFolderList;
    private DefaultListModel<String> outputFolderModel;
    private JList<String> outputFolderList;
    private JButton processButton;
    private JList<String> failedFilesList;
    private DefaultListModel<String> failedFilesModel;
    private Map<String, List<String>> failedImagesMap;  // Map to store md file and its missing images
    private File outputFolder;

    public MarkdownExtractorGUI() {
        setTitle("Markdown Extractor");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 佈局調整：左邊 Input Folder 列表，右邊 Output Folder 單一文本框
        JPanel panelGrid1 = new JPanel(new GridLayout(1, 2, 30, 30));
        panelGrid1.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Input Folder 左邊
        JPanel inputPanel = new JPanel(new BorderLayout());
        JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
        inputFolderModel = new DefaultListModel<>();
        inputFolderList = new JList<>(inputFolderModel);
        inputFolderList.setTransferHandler(new FileDropHandler(true));  // 支援拖放資料夾或文件
        JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

        inputPanel.add(inputLabel, BorderLayout.NORTH);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        panelGrid1.add(inputPanel);

        // Output Folder 右邊
        JPanel outputPanel = new JPanel(new BorderLayout());
        JLabel outputLabel = new JLabel("Output Folders:");
        outputFolderModel = new DefaultListModel<>();
        outputFolderList = new JList<>(outputFolderModel);
        outputFolderList.setTransferHandler(new FileDropHandler(false));  // 支援拖放資料夾
        JScrollPane outputScrollPane = new JScrollPane(outputFolderList);  // 添加 ScrollPane 以支持多個輸出資料夾

        outputPanel.add(outputLabel, BorderLayout.NORTH);
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);
        panelGrid1.add(outputPanel);

        add(panelGrid1, BorderLayout.NORTH);

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
        add(panelGrid2, BorderLayout.CENTER);

        // Process Markdown 按鈕
        processButton = new JButton("Process Markdown");
        processButton.addActionListener(e -> processMarkdown());
        add(processButton, BorderLayout.SOUTH);

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
    }

    // TransferHandler to handle file and folder drops
    private class FileDropHandler extends TransferHandler {
        private boolean isInput;

        public FileDropHandler(boolean isInput) {
            this.isInput = isInput;
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
                                inputFolderModel.addElement(selectedFile.getAbsolutePath());
                            }
                        } else {
                            if (selectedFile.isDirectory()) {
                                // 如果已有一個輸出資料夾，則清空列表，然後添加新的資料夾
                                if (outputFolderModel.size() > 0) {
                                    outputFolderModel.clear();
                                }
                                outputFolderModel.addElement(selectedFile.getAbsolutePath());
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

    private void processMarkdown() {
        if (inputFolderModel.isEmpty() || outputFolderModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select input and output folders or files.");
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

            JOptionPane.showMessageDialog(this, "Processing completed.");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "An error occurred: " + e.getMessage());
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MarkdownExtractorGUI().setVisible(true));
    }
}
