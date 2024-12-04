module org.example.markdownextractor {
    requires java.desktop;// 用於 Swing 和 AWT GUI
    requires org.apache.poi.poi; // xls
    requires com.fasterxml.jackson.databind; //json
    opens org.example to com.fasterxml.jackson.databind;
}