package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.util.ArrayList;
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
    ws.Columns.AutoFit
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
        String[] columnNames = {"From Column", "To Column", "Text"};
        tableModel = new DefaultTableModel(columnNames, 0);
        columnTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(columnTable);
        add(scrollPane, BorderLayout.CENTER);

        // 底部按鈕
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add Column");
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

        List<ColumnConfig> columnConfigs = new ArrayList<>();
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
                to = from; // 如果 toColumn 無效或為空，設為 fromColumn
            }


            if (textObj != null) {
                text = textObj.toString().trim(); // 確保 text 是字串並去除空格
            }

            columnConfigs.add(new ColumnConfig(from, to, text));
        }

        // 寫入 XLS
        try {
            List<String> lines = readTxtFile(filePath);
            String outputFilePath = filePath.replace(".txt", ".xls");
            writeToXls(lines, columnConfigs, outputFilePath);
            JOptionPane.showMessageDialog(this, "Conversion completed! Output: " + outputFilePath);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error during conversion: " + ex.getMessage());
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

    private void writeToXls(List<String> lines, List<ColumnConfig> columnConfigs, String outputFilePath) throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Converted Data");

        // 標題列
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columnConfigs.size(); i++) {
            headerRow.createCell(i).setCellValue(columnConfigs.get(i).text);
        }

        // 資料行
        for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
            String line = lines.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            for (int colIndex = 0; colIndex < columnConfigs.size(); colIndex++) {
                ColumnConfig config = columnConfigs.get(colIndex);
                String cellData = extractSubstring(line, config.fromColumn - 1, config.toColumn);
                row.createCell(colIndex).setCellValue(cellData);
            }
        }

        // 輸出為 XLS
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    //
    private String extractSubstring(String line, int fromIndex, int toIndex) {
        if (fromIndex >= line.length()) return "";
        if (toIndex > line.length()) toIndex = line.length();
        //return line.substring(fromIndex, toIndex).trim();
        return line.substring(fromIndex, toIndex);
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
            List<ColumnConfig> columnConfigs = getColumnConfigs();
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
                ColumnConfig[] configs = objectMapper.readValue(fileToLoad, ColumnConfig[].class);
                tableModel.setRowCount(0); // 清空表格
                for (ColumnConfig config : configs) {
                    tableModel.addRow(new Object[]{config.fromColumn, config.toColumn, config.text});
                }
                JOptionPane.showMessageDialog(this, "Configuration loaded successfully from " + fileToLoad.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error loading configuration: " + e.getMessage());
            }
        }
    }

    private List<ColumnConfig> getColumnConfigs() {
        List<ColumnConfig> columnConfigs = new ArrayList<>();
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

            columnConfigs.add(new ColumnConfig(from, to, text));
        }
        return columnConfigs;
    }



    static class ColumnConfig {
        @JsonProperty("fromColumn")
        private int fromColumn;

        @JsonProperty("toColumn")
        private Integer toColumn; // 使用 Integer 允許 null 值

        @JsonProperty("text")
        private String text;

        // 無參構造函數（Jackson 需要）
        public ColumnConfig() {}

        // 有參構造函數
        public ColumnConfig(int fromColumn, Integer toColumn, String text) {
            this.fromColumn = fromColumn;
            this.toColumn = toColumn;
            this.text = text;
        }

        // Getter 和 Setter
        public int getFromColumn() {
            return fromColumn;
        }

        public void setFromColumn(int fromColumn) {
            this.fromColumn = fromColumn;
        }

        public Integer getToColumn() {
            // 如果 toColumn 為 null，回傳預設值 -1 或其他預設值
            return toColumn != null ? toColumn : null; // 可根據需求設定預設值
        }

        public void setToColumn(Integer toColumn) {
            this.toColumn = toColumn;
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
