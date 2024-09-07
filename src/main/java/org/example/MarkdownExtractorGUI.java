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
    private JTextField outputFolderField;
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
        JPanel panel = new JPanel(new GridLayout(1, 2, 30, 30));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Input Folder 左邊
        JPanel inputPanel = new JPanel(new BorderLayout());
        JLabel inputLabel = new JLabel("Input Folders or Markdown Files:");
        inputFolderModel = new DefaultListModel<>();
        inputFolderList = new JList<>(inputFolderModel);
        inputFolderList.setTransferHandler(new FileDropHandler(true));  // 支援拖放資料夾或文件
        JScrollPane inputScrollPane = new JScrollPane(inputFolderList);  // 添加 ScrollPane 以支持多個來源

        inputPanel.add(inputLabel, BorderLayout.NORTH);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        panel.add(inputPanel);

        // Output Folder 右邊
        JPanel outputPanel = new JPanel(new BorderLayout());
        JLabel outputLabel = new JLabel("Output Folder:");
        outputFolderField = new JTextField();
        outputFolderField.setEditable(false);
        outputFolderField.setTransferHandler(new FileDropHandler(false));  // 支援拖放資料夾
        outputPanel.add(outputLabel, BorderLayout.NORTH);
        outputPanel.add(outputFolderField, BorderLayout.CENTER);
        panel.add(outputPanel);

        add(panel, BorderLayout.NORTH);

        // Failed files list (with scroll bar)
        failedFilesModel = new DefaultListModel<>();
        failedFilesList = new JList<>(failedFilesModel);
        JScrollPane scrollPane = new JScrollPane(failedFilesList);
        add(scrollPane, BorderLayout.CENTER);

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
                            outputFolder = selectedFile;
                            outputFolderField.setText(selectedFile.getAbsolutePath());
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
        if (inputFolderModel.isEmpty() || outputFolder == null) {
            JOptionPane.showMessageDialog(this, "Please select input and output folders or files.");
            return;
        }

        failedFilesModel.clear();
        failedImagesMap.clear();

        try {
            Files.createDirectories(Paths.get(outputFolder.getAbsolutePath()));

            for (int i = 0; i < inputFolderModel.size(); i++) {
                File selectedFile = new File(inputFolderModel.get(i));

                if (selectedFile.isDirectory()) {
                    processDirectory(selectedFile);  // 處理資料夾中的 .md 文件
                } else if (selectedFile.getName().endsWith(".md")) {
                    processMarkdownFile(selectedFile);  // 處理單一 .md 文件
                }
            }

            JOptionPane.showMessageDialog(this, "Processing completed.");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "An error occurred: " + e.getMessage());
        }
    }

    private void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".md"));
        if (files != null) {
            for (File file : files) {
                processMarkdownFile(file);
            }
        }
    }

    private void processMarkdownFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        List<String> missingImages = new ArrayList<>();

        // 使用兩個正則表達式來匹配 Markdown 和 HTML 中的圖片
        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
        Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(content);

        // 處理 Markdown 圖片
        while (markdownMatcher.find()) {
            String imageUrl = markdownMatcher.group(1);
            processImage(file, imageUrl, missingImages);
        }

        // 處理 HTML 圖片
        while (htmlMatcher.find()) {
            String imageUrl = htmlMatcher.group(1);
            processImage(file, imageUrl, missingImages);
        }

        if (!missingImages.isEmpty()) {
            failedFilesModel.addElement(file.getAbsolutePath());
            failedImagesMap.put(file.getAbsolutePath(), missingImages);
        }
    }

    private void processImage(File file, String imageUrl, List<String> missingImages) {
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
                File outputFile = new File(outputFolder, imageName);
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
