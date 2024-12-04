package org.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/*
* EXCEL 可以使用，讓標題變好看。
* Sub AutoWrapTextFirstRowOnly()
    Dim ws As Worksheet
    Dim cell As Range

    Set ws = ActiveSheet

    ' 只遍歷第一行
    For Each cell In ws.Rows(1).Cells
        If Len(cell.Value) > 8 Then ' 字超過 8 個字元時換行
            cell.Value = Left(cell.Value, 8) & vbLf & Mid(cell.Value, 9)
            cell.WrapText = True
        End If
    Next cell

    ' 自適應大小
    ws.ByteColumns.AutoFit
End Sub
*
*
* */
public class TxtToXlsConverter extends JFrame {
    private JTextField filePathField;
    private JTable columnTable;
    private DefaultTableModel tableModel;

    public TxtToXlsConverter() {
        setTitle("Txt To Xls Converter");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 檔案路徑顯示區域
        JPanel filePanel = new JPanel(new BorderLayout());
        filePathField = new JTextField("Drag and Drop a File Here");
        filePathField.setEditable(false);
        filePanel.add(filePathField, BorderLayout.CENTER);

        // DropTarget 支援拖放功能
        new DropTarget(filePathField, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {}

            @Override
            public void dragOver(DropTargetDragEvent dtde) {}

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}

            @Override
            public void dragExit(DropTargetEvent dte) {}

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        filePathField.setText(droppedFiles.get(0).getAbsolutePath());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        add(filePanel, BorderLayout.NORTH);

        // 欄位設定表格
        String[] columnNames = {"From ByteColumn", "To ByteColumn", "Text"};
        tableModel = new DefaultTableModel(columnNames, 0);
        columnTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(columnTable);
        add(scrollPane, BorderLayout.CENTER);

        // 底部按鈕
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add ByteColumn");
        addButton.addActionListener(e -> tableModel.addRow(new Object[]{"", "", ""}));

        JButton convertButton = new JButton("Convert");
        convertButton.addActionListener(e -> convertToXls());

        // 保存和讀取按鈕
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveConfiguration());

        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(e -> loadConfiguration());

        buttonPanel.add(addButton);
        buttonPanel.add(convertButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(loadButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void convertToXls() {
        String filePath = filePathField.getText();
        if (filePath.isEmpty() || filePath.equals("Drag and Drop a File Here")) {
            JOptionPane.showMessageDialog(this, "Please drag and drop a file first!");
            return;
        }

        List<ByteColumnConfig> columnConfigs = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object fromObj = tableModel.getValueAt(i, 0);
            Object toObj = tableModel.getValueAt(i, 1);
            Object textObj = tableModel.getValueAt(i, 2);

            // 檢查是否所有欄位都為空，若是則跳過此列
            if ((fromObj == null || fromObj.toString().trim().isEmpty()) &&
                    (toObj == null || toObj.toString().trim().isEmpty()) &&
                    (textObj == null || textObj.toString().trim().isEmpty())) {
                continue; // 若整列都是空的，跳過該列
            }

            int from = 0;
            int to = 0;
            String text = "";

            // 確認資料類型後進行轉換
            if (fromObj instanceof Integer) {
                from = (Integer) fromObj;
            } else if (fromObj instanceof String && !((String) fromObj).trim().isEmpty()) {
                from = Integer.parseInt((String) fromObj);
            }

            if (toObj instanceof Integer) {
                to = (Integer) toObj;
            } else if (toObj instanceof String && !((String) toObj).trim().isEmpty()) {
                to = Integer.parseInt((String) toObj);
            } else {
                to = from; // 如果 toByteColumn 無效或為空，設為 fromByteColumn
            }


            if (textObj != null) {
                text = textObj.toString().trim(); // 確保 text 是字串並去除空格
            }

            columnConfigs.add(new ByteColumnConfig(from, to, text));
        }

        // 寫入 XLS
        try {
            List<String> lines = readTxtFile(filePath);
            String outputFilePath = filePath.replace(".txt", ".xls");
            writeToXls(lines, columnConfigs, outputFilePath);
            JOptionPane.showMessageDialog(this, "Conversion completed! Output: " + outputFilePath);
        } catch (Exception ex) {
            System.out.println(ex.toString());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error during conversion: " + ex.toString());
        }
    }


    private List<String> readTxtFile(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
    /**
     * 每隔指定數量的字元插入換行符號。
     *
     * @param text 原始字符串。
     * @param interval 每隔多少個字元插入一次換行。
     * @return 格式化後的字符串，包含換行符號。
     */
    private String insertLineBreaks(String text, int interval) {
        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i += interval) {
            if (i + interval < length) {
                sb.append(text, i, i + interval).append("\n");
            } else {
                sb.append(text.substring(i));
            }
        }
        return sb.toString();
    }
    private void writeToXls(List<String> lines, List<ByteColumnConfig> columnConfigs, String outputFilePath) throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Converted Data");

        // 標題列

        // 創建自動換行的單元格樣式
        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        wrapStyle.setAlignment(HorizontalAlignment.CENTER);
        wrapStyle.setWrapText(true);
        Row headerRow = sheet.createRow(0);
        int cellIndex = 0;
        for (ByteColumnConfig config : columnConfigs) {
            Cell cell = headerRow.createCell(cellIndex++);
            cell.setCellValue(config.text);

            // 檢查標題內容長度
            if (config.text.length() > 8) {
                String newValue = insertLineBreaks(config.text,8);
                cell.setCellValue(newValue);
            }
            cell.setCellStyle(wrapStyle);
        }
        // 添加「長度符合」欄位標題
        headerRow.createCell(cellIndex).setCellValue("長度符合");

        // 資料行
        int maxWidth = 0; // 不能auto去判斷寬，只適用標題列而已
        HashMap<Integer,Integer> maxWidthMap= new HashMap<>();
        for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
            String line = lines.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);//cuz 標題列

            for (int colIndex = 0; colIndex < columnConfigs.size(); colIndex++) {
                ByteColumnConfig config = columnConfigs.get(colIndex);
                String cellData = extractExactlyByteSubstring(line, config);
                row.createCell(colIndex).setCellValue(cellData);
                // 計算框框的大小，找最大就好

                maxWidth = Math.max(
                        maxWidthMap.getOrDefault(colIndex,0),
                        cellData.getBytes(Charset.forName("UTF-8")).length);
                maxWidthMap.put(colIndex, maxWidth);
                // 計算 bytes 總長度是否符合
                if (colIndex == (columnConfigs.size()-1)){
                    // 指定使用 Big5 編碼
                    Charset big5 = Charset.forName("Big5");
                    // 將字符串轉換為 Big5 編碼的字節數組
                    byte[] bytes = line.getBytes(big5);
                    if(bytes.length == config.toByteColumn){
                        row.createCell(colIndex+1).setCellValue("OK");
                    }
                    else{
                        row.createCell(colIndex+1).setCellValue("GG: 應該 (" +config.toByteColumn +") 實際 ("+bytes.length+")");
                    }
                }
                // 乘以 256 是 POI 的列寬單位
                System.out.println("最大"+maxWidth);
            }
        }
        // 固定每列寬度
        for (int colIndex = 0; colIndex < columnConfigs.size(); colIndex++) {
            sheet.setColumnWidth(colIndex, (maxWidthMap.get(colIndex) + 1) * 256); // 5000 是一個合理的寬度（大約 20 個字元）
        }
        // 輸出為 XLS
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    //
    private String extractExactlyByteSubstring(String line,ByteColumnConfig config) {

        // 1 1 => 1 byte；2 4 => 3 bytes
        int blockTotalBytes = (config.toByteColumn - config.fromByteColumn +1);
        // 指定使用 Big5 編碼
        Charset big5 = Charset.forName("Big5");
        // 將字符串轉換為 Big5 編碼的字節數組
        byte[] bytes = line.getBytes(big5);
        try {
            boolean isValid = isValidByteRange(bytes, config.fromByteColumn - 1, config.toByteColumn, big5);
            if (isValid){
                // 如果有效，進行解碼
                String subset = new String(bytes, config.fromByteColumn-1,
                        config.toByteColumn - (config.fromByteColumn-1),
                        big5);
                return subset + "【"+ "應佔:"+ (config.toByteColumn - (config.fromByteColumn-1)) +"實際:"+ subset.getBytes(big5).length  +"】";
            }else{
                // 無效
                return "ERROR(truncated轉譯失敗)";
            }
        }catch(Exception e){
            e.printStackTrace();
            return "ERROR2(outOfBound)";
        }

    }


    /**
     * 檢查指定的字節範圍是否為有效的字符序列。
     * 這可以用來確保在多字節編碼（如 Big5）中不會截斷字符，從而避免解碼錯誤或亂碼。
     *
     * @param bytes    原始string 轉 bytes 的陣列。
     * @param start    開始檢查的起始索引（基於 0） 【若起始為1，需-1】。
     * @param end      結束檢查的索引（不包含該索引位置的字節）【若起始為1，不須-1】。
     * @param charset  使用的字符集（例如 Big5）。
     * @return 如果字節範圍內的字節序列是有效的，返回 true；否則返回 false。
     */
    private boolean isValidByteRange(byte[] bytes, int start, int end, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, start, end - start);
            decoder.decode(byteBuffer);
            return true; // 字節範圍內的字節序列是有效的
        } catch (CharacterCodingException e) {
            return false; // 字節範圍內的字節序列無效，可能破壞了多字節字符
        }
    }


    private void saveConfiguration() {
        // 使用檔案選擇器來選擇要儲存的設定檔案
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Configuration");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            ObjectMapper objectMapper = new ObjectMapper();
            List<ByteColumnConfig> columnConfigs = getByteColumnConfigs();
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileToSave, columnConfigs);
                JOptionPane.showMessageDialog(this, "Configuration saved successfully to " + fileToSave.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving configuration: " + e.getMessage());
            }
        }
    }

    private void loadConfiguration() {
        // 使用檔案選擇器來選擇要讀取的設定檔案
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Configuration");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int userSelection = fileChooser.showOpenDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToLoad = fileChooser.getSelectedFile();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                ByteColumnConfig[] configs = objectMapper.readValue(fileToLoad, ByteColumnConfig[].class);
                tableModel.setRowCount(0); // 清空表格
                for (ByteColumnConfig config : configs) {
                    tableModel.addRow(new Object[]{config.fromByteColumn, config.toByteColumn, config.text});
                }
                JOptionPane.showMessageDialog(this, "Configuration loaded successfully from " + fileToLoad.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error loading configuration: " + e.getMessage());
            }
        }
    }

    private List<ByteColumnConfig> getByteColumnConfigs() {
        List<ByteColumnConfig> columnConfigs = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object fromObj = tableModel.getValueAt(i, 0);
            Object toObj = tableModel.getValueAt(i, 1);
            Object textObj = tableModel.getValueAt(i, 2);

            // 檢查是否所有欄位都為空，若是則跳過此列
            if ((fromObj == null || fromObj.toString().trim().isEmpty()) &&
                    (toObj == null || toObj.toString().trim().isEmpty()) &&
                    (textObj == null || textObj.toString().trim().isEmpty())) {
                continue; // 若整列都是空的，跳過該列
            }

            int from = 0;
            Integer to = null; //這樣預設才會是 null
            String text = "";

            // 確認資料類型後進行轉換
            if (fromObj instanceof Integer) {
                from = (Integer) fromObj;
            } else if (fromObj instanceof String && !((String) fromObj).trim().isEmpty()) {
                from = Integer.parseInt((String) fromObj);
            }

            if (toObj instanceof Integer) {
                to = (Integer) toObj;
            } else if (toObj instanceof String && !((String) toObj).trim().isEmpty()) {
                to = Integer.parseInt((String) toObj);
                System.out.println(to);
            }

            if (textObj != null) {
                text = textObj.toString().trim(); // 確保 text 是字串並去除空格
            }

            columnConfigs.add(new ByteColumnConfig(from, to, text));
        }
        return columnConfigs;
    }



    static class ByteColumnConfig {
        @JsonProperty("fromByteColumn")
        private int fromByteColumn;

        @JsonProperty("toByteColumn")
        private Integer toByteColumn; // 使用 Integer 允許 null 值

        @JsonProperty("text")
        private String text;

        // 無參構造函數（Jackson 需要）
        public ByteColumnConfig() {}

        // 有參構造函數
        public ByteColumnConfig(int fromByteColumn, Integer toByteColumn, String text) {
            this.fromByteColumn = fromByteColumn;
            this.toByteColumn = toByteColumn;
            this.text = text;
        }

        // Getter 和 Setter
        public int getFromByteColumn() {
            return fromByteColumn;
        }

        public void setFromByteColumn(int fromByteColumn) {
            this.fromByteColumn = fromByteColumn;
        }

        public Integer getToByteColumn() {
            // 如果 toByteColumn 為 null，回傳預設值 -1 或其他預設值
            return toByteColumn != null ? toByteColumn : null; // 可根據需求設定預設值
        }

        public void setToByteColumn(Integer toByteColumn) {
            this.toByteColumn = toByteColumn;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TxtToXlsConverter gui = new TxtToXlsConverter();
            gui.setVisible(true);
        });
    }
}
