package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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

    private String HowCheckItemFill= """
                    1.檢核方式空白要檢核需要前後都有分號，例如【;  ;A】>>>>> A和空白*2
                    2.【regex(^\\d{3}\\.\\d{1}$);】  >>>> 可以讓 025.0 格式識別
                    3.【regex(^\\d{10}$);A;略】 >> A 和 10個數字組成的
                    4.【A;B;C; ;】 >>>A、B、C、空白
                    5. from 一定要填，to可以省略 -------------------省事
            """;
    private JTextField filePathField;
    private JTable columnTable;
    private DefaultTableModel tableModel;

    public TxtToXlsConverter() {
        setTitle("Txt To Xls Converter");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        // 檢核說明區域
        JPanel infoPanel = new JPanel(new BorderLayout());
        JTextArea infoArea = new JTextArea(HowCheckItemFill);
        infoArea.setEditable(false);
        infoArea.setLineWrap(true); // 啟用自動換行
        infoArea.setWrapStyleWord(true); // 使換行更美觀
        infoArea.setBackground(getBackground()); // 設置與 GUI 背景一致
        infoArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10)); // 設置邊距
        infoPanel.add(infoArea, BorderLayout.NORTH);
        // 檔案路徑顯示區域
        JPanel filePanel = new JPanel(new BorderLayout());
        filePathField = new JTextField("【Drag and Drop a File Here】.....check條件，必須緊湊，例如:A;B; ;regex(放regex格式);");
        // 增加 JTextField 的首選高度
        filePathField.setPreferredSize(new Dimension(filePathField.getPreferredSize().width, 40)); // 高度設為 40px
        filePathField.setEditable(false);
        filePanel.add(infoPanel, BorderLayout.NORTH);// 將說明放置在最頂部
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
        String[] columnNames = {"From ByteColumn", "To ByteColumn", "Text", "Check"};
        tableModel = new DefaultTableModel(columnNames, 0);
        columnTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(columnTable);
        add(scrollPane, BorderLayout.CENTER);

        // 底部按鈕
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add ByteColumn");
        addButton.addActionListener(e -> tableModel.addRow(new Object[]{"", "", "", ""}));

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
        if (filePath.isEmpty() || filePath.equals("【Drag and Drop a File Here】.....check條件，必須緊湊，例如:A;B; ;regex(放regex格式);")) {
            JOptionPane.showMessageDialog(this, "Please drag and drop a file first!");
            return;
        }

        List<ByteColumnConfig> columnConfigs = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object fromObj = tableModel.getValueAt(i, 0);
            Object toObj = tableModel.getValueAt(i, 1);
            Object textObj = tableModel.getValueAt(i, 2);
            Object checkObj = tableModel.getValueAt(i, 3);

            // 檢查是否所有欄位都為空，若是則跳過此列
            if ((fromObj == null || fromObj.toString().trim().isEmpty()) &&
                    (toObj == null || toObj.toString().trim().isEmpty()) &&
                    (textObj == null || textObj.toString().trim().isEmpty()) &&
                    (checkObj == null || checkObj.toString().trim().isEmpty())) {
                continue; // 若整列都是空的，跳過該列
            }

            int from = 0;
            int to = 0;
            String text = "";
            String check = "";

            // 解析 `from` 欄位
            try {
                if (fromObj instanceof Integer) {
                    from = (Integer) fromObj;
                } else if (fromObj instanceof String && !((String) fromObj).trim().isEmpty()) {
                    from = Integer.parseInt((String) fromObj);
                }
            } catch (NumberFormatException e) {
                // 顯示例外訊息到控制台和 MsgBox
                System.out.println("解析 from 欄位時發生錯誤: " + e.getMessage());
                JOptionPane.showMessageDialog(null, "解析 from 欄位時發生錯誤: " + e.getMessage());
                return; // 結束處理
            }

            // 解析 `to` 欄位
            try {
                if (toObj instanceof Integer) {
                    to = (Integer) toObj;
                } else if (toObj instanceof String && !((String) toObj).trim().isEmpty()) {
                    to = Integer.parseInt((String) toObj);
                } else {
                    to = from; // 如果 toByteColumn 無效或為空，設為 fromByteColumn
                }
            } catch (NumberFormatException e) {
                // 顯示例外訊息到控制台和 MsgBox
                System.out.println("解析 to 欄位時發生錯誤: " + e.getMessage());
                JOptionPane.showMessageDialog(null, "解析 to 欄位時發生錯誤: " + e.getMessage());
                return; // 結束處理
            }

            // 確保文字與條件欄位為字串
            if (textObj != null) {
                text = textObj.toString().trim();
            }
            if (checkObj != null) {
                check = checkObj.toString().trim();
            }

            columnConfigs.add(new ByteColumnConfig(from, to, text, check));
        }

        // 寫入 XLS
        try {
            List<String> lines = readTxtFile(filePath);
            String outputFilePath = filePath.replace(".txt", ".xls");
            writeToXls(lines, columnConfigs, outputFilePath);
            JOptionPane.showMessageDialog(this, "Conversion completed! Output: " + outputFilePath);
        } catch (Exception ex) {
            // 顯示例外訊息到控制台和 MsgBox
            System.out.println("轉換過程中發生錯誤: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "轉換過程中發生錯誤: " + ex.getMessage());
        }
    }



    private List<String> readTxtFile(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
// 預設狀態可能會是 utf8 所以導致讀到的 line
//      原始line:D45111131212305479111111111111122222222221                    3333333333N4444444444N5555555555N���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q�Ӧr���Q066.6
//        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                lines.add(line);
//            }
//        }
        // 改用 Big5 指定方式
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "Big5"))) {
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
        Row checkRow = sheet.createRow(1);
        int cellIndex = 0;
        for (ByteColumnConfig config : columnConfigs) {
            Cell cellHeader = headerRow.createCell(cellIndex);
            Cell cellCheck = checkRow.createCell(cellIndex);
            cellIndex++;
            cellHeader.setCellValue(config.text);
            cellCheck.setCellValue(config.check);

            // 檢查標題內容長度
            if (config.text.length() > 8) {
                String newValue = insertLineBreaks(config.text,8);
                cellHeader.setCellValue(newValue);
            }
            // 檢查check內容長度
            if (config.check.length() > 20) {
                String newValue = insertLineBreaks(config.check,10);
                cellCheck.setCellValue(newValue);
            }

            cellHeader.setCellStyle(wrapStyle);
            cellCheck.setCellStyle(wrapStyle);
        }
        // 添加「長度符合」欄位標題
        headerRow.createCell(cellIndex).setCellValue("長度符合");

        // 資料行
        int maxWidth = 0; // 不能auto去判斷寬，只適用標題列而已
        HashMap<Integer,Integer> maxWidthMap= new HashMap<>();
        for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
            String line = lines.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 2);//cuz 標題列、條件列

            for (int colIndex = 0; colIndex < columnConfigs.size(); colIndex++) {
                ByteColumnConfig config = columnConfigs.get(colIndex);
                String cellData = extractExactlyByteSubstring(line, config);
                cellData = checkCellData(cellData,config);
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

    private String checkCellData(String cellData, ByteColumnConfig config) {
        int index = cellData.indexOf("【");
        String cellDataMetaData = "";
        if(index != -1){
            if (config.check.trim().isEmpty())
                return cellData + cellDataMetaData + "沒寫條件檢核";
            // cellData = "AAAAA【應該5 實際5】"
            cellDataMetaData = cellData.substring(index); // "【應該5 實際5】"
            cellData = cellData.substring(0, index); // "AAAAA"
            String[] conditions = config.check.split(";"); // 格式條件
            // condition[0] = "A"
            // condition[1] = "B"
            // condition[2] = "C"
            // condition[3] = " "  則 cellData 應該符合以上任一才行
            // condition[4] = "regex(^\d{8}$)"  例如左邊 則 cellData 需要是8位數字 才能pass
            String checkResult = "";
            boolean matchFound = false;



            for(String condition : conditions){

                if(condition.startsWith("regex(") && condition.trim().endsWith(")")){
                    // 提取正則表達式
                    String regexPattern = condition.substring(6, condition.length() - 1);
                    if(cellData.matches(regexPattern)){
                        matchFound = true;
                        break;
                    }
                } else {
                    // 簡單的字符串比較
                    if(cellData.equals(condition)){
                        matchFound = true;
                        break;
                    }
                    if(condition.equals("略")){
                        return cellData + cellDataMetaData + "觸發略字";
                    }
                }
            }

            if(matchFound){
                checkResult = "[PASS]"; // 檢查通過
            } else {
                checkResult = "（檢查不通過）";
            }

            return cellData + cellDataMetaData + checkResult;

        } else {
            System.out.println("cell 內有 ERROR 不處理");
            // "ERROR(truncated轉譯失敗)";
            // "ERROR2(outOfBound)";
            return cellData;
        }
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
            // bytes 實際上由0開始，但 config 則是 1 開始。
            boolean isValid = isValidByteRange(bytes, config.fromByteColumn - 1, config.toByteColumn, big5);
            if (isValid){
                // 如果有效，進行解碼
                int offset = config.fromByteColumn-1;
                int length = config.toByteColumn - (config.fromByteColumn-1);
//                System.out.println("原始line:"+line);
//                System.out.println("輸出::"+new String(bytes, 0,bytes.length,big5));
                String subset = new String(bytes, offset,length, big5);

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
                    tableModel.addRow(new Object[]{config.fromByteColumn, config.toByteColumn, config.text, config.check});
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
            Object checkObj = tableModel.getValueAt(i, 3); // 添加這一行

            // 檢查是否所有欄位都為空，若是則跳過此列
            if ((fromObj == null || fromObj.toString().trim().isEmpty()) &&
                    (toObj == null || toObj.toString().trim().isEmpty()) &&
                    (textObj == null || textObj.toString().trim().isEmpty()) &&
                    (checkObj == null || checkObj.toString().trim().isEmpty())) { // 修改這一行
                continue; // 若整列都是空的，跳過該列
            }

            int from = 0;
            Integer to = null; // 這樣預設才會是 null
            String text = "";
            String check = ""; // 添加這一行

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

            if (checkObj != null) {
                check = checkObj.toString().trim(); // 確保 check 是字串並去除空格
            }

            columnConfigs.add(new ByteColumnConfig(from, to, text, check)); // 添加 check 參數
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

        @JsonProperty("check")
        private String check;

        // 無參構造函數（Jackson 需要）
        public ByteColumnConfig() {}

        // 有參構造函數
        public ByteColumnConfig(int fromByteColumn, Integer toByteColumn, String text, String check) {
            this.fromByteColumn = fromByteColumn;
            this.toByteColumn = toByteColumn;
            this.text = text;
            this.check = check;
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

        public String getCheck() {
            return check;
        }

        public void setCheck(String check) {
            this.check = check;
        }
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TxtToXlsConverter gui = new TxtToXlsConverter();
            gui.setVisible(true);
        });
    }
}
