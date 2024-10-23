# 使用 Windows 筆記本上的 Docker 模擬 Raspberry Pi 4B 環境

# 1. 在 Windows 上安裝 Docker Desktop：

- 在 Windows 筆記本上安裝 **Docker Desktop**，並啟用 **WSL 2** 來更好地支援 Linux 容器。

- Docker Desktop 已自帶 **Docker Compose**，無需單獨安裝。

# 2. 拉取適合 Raspberry Pi 的 Ubuntu 映像：

- 使用 Docker 拉取支持 **ARM 架構**的 Ubuntu 映像來模擬 Raspberry Pi 4B 的環境。

- 執行以下命令：
  
  ```batch
  docker pull arm64v8/ubuntu:latest
  ```

# 3. 建立 Docker 容器來模擬 A Pi 和 B Pi 環境：

## 容器A 【 A-pi-ubuntu 】

- 創建兩個 Docker 容器來模擬 A Pi 和 B Pi 的環境。
  
  > 容器 A = server
  
  ```batch
  docker run -it --platform linux/arm64 --name A-pi-ubuntu arm64v8/ubuntu:latest /bin/bash容器 B = Camera
  ```

## 容器B 【 B-pi-camera 】

如果要讓 **B-Camera-pi** 容器能接收來自 Windows 主機的 RTSP 流，您需要映射對應的端口，以便主機和 Docker 容器之間可以正確地互相通信。

### 配置步驟：

1. **主機推送 RTSP 流**：
   
   - 使用 Windows 上的 FFmpeg 將本地攝像頭的流推送到 
     
     [rtsp://localhost:8555/stream]()  
   
   - 需要使用 rtsp server ( GPT 說使用 MediaMTX RTSP )

2. **映射端口給 Docker 容器**：
   
   - 在啟動 **B-Camera-pi** 容器時，您需要映射主機的 8554 端口給容器，使容器可以訪問 RTSP 流。例如：
     
     > docker run -it --platform linux/arm64 --name B-Camera-pi-ubuntu -p 8554:8554 arm64v8/ubuntu:latest /bin/bash
     
     ```batch
     docker run -it --platform linux/arm64 --name B-Camera-pi-ubuntu -p 8554:8554 arm64v8/ubuntu:latest /bin/bash
     ```
   
   - `-p 8554:8554` 將主機的 8554 端口映射到容器的 8554 端口，
     
     這樣 B 容器可以通過 RTSP 來訪問主機推送的流。

3. **在容器中拉取 RTSP 流**：
   
   - 在 **B-Camera-pi** 容器內，先執行以下命令來更新並安裝 **FFmpeg** :
     
     ```batch
     apt-get update
     apt-get install -y ffmpeg
     ```
   
   - 如果 Docker 容器和主機在同一個網絡中，可以使用 Windows 主機的 IP 來連接。
     
     例如，如果主機 IP 是 `192.168.1.100`
     
     那麼 RTSP URL 將是 [rtsp://192.168.1.100:8554/stream]()。 
   
   - 詳細會在下邊標題 [5. Windows 操作 FFmpeg 傳遞攝影機影像給容器]() 說明

這樣，您就可以將 Windows 上攝像頭的流推送給 Docker 容器中的 **B-Camera-pi**，

並進一步處理這些流。

# 4. 在模擬環境中開發和測試應用：

- 在 A Pi 和 B Pi 容器中安裝所需的軟件包和依賴，例如 **FFmpeg**、**Node.js** 等：
  
  ```batch
  apt-get update
  apt-get install -y ffmpeg nodejs npm
  ```

- 您可以在 B Pi 容器中模擬攝像頭，並在 A Pi 容器中接收並處理這些視頻流。

# 5. Windows 操作 FFmpeg 傳遞攝影機影像給容器

---

| 稍微注意看一下唷 |
| -------- |

> 基本上只讓裡面可以讀取 rtsp stream 就好，

> 雖然會報錯無法 顯示 ( 因為容器沒有做額外設定並不能顯示)

> 只要 之後改寫 python 讓它保存10秒的 .ts 影片就行。
> 
> 目前python code 是下方標題 所撰寫的程式碼
> 
> [7. B camera 建立資料夾於 home 之下，放code。]() 使用這個有用到 cv2.imshow
> 所以會報出錯誤。

- 如果弄到這樣之後，這份文件可以停止了，去看
  
  [步驟2 - 讀取 rtsp stream 並儲存為 ts檔.md]()  

---

## 查找容器 B 的 docker 網路中的IP 位置

- 其他容器( A server ) 可能會使用，先知道就行。

> docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' B-Camera-pi-ubuntu

```batch
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' B-Camera-pi-ubuntu
```

## 查找 ffmpeg 可以使用的 設備

> ffmpeg -list_devices true -f dshow -i dummy

```batch
ffmpeg -list_devices true -f dshow -i dummy
```

## FFmpeg + MediaMTX RTSP 伺服器設置筆記

| 此區塊請先完成 6. 7. 8. 9.  再回來閱讀 |
| -------------------------- |

這篇筆記將教你如何使用 FFmpeg 將視頻流推送到 MediaMTX RTSP 伺服器，並通過客戶端接收和播放這些流。整個流程簡單有效，非常適合本地或局域網內的流媒體設置。

---

### 1. 準備工作

- **FFmpeg**：安裝 FFmpeg 來捕獲和推送視頻流，可以在 [FFmpeg 官方網站](https://ffmpeg.org/download.html) 下載適合的平台版本。
- **MediaMTX**：這是 RTSP 伺服器軟體，負責接收並分發視頻流。你可以從 [GitHub](https://github.com/bluenviron/mediamtx/releases) 下載並解壓 `mediamtx` 到合適位置。

---

### 2. 啟動 MediaMTX RTSP 伺服器

1. **進入安裝目錄**，在命令提示符中啟動伺服器：
   
   ```bash
   .\mediamtx.exe
   ```

2. 如果遇到錯誤，例如 `8554` 端口被佔用，修改配置文件 `mediamtx.yml` 中的 `rtspAddress` 端口號，例如改為 `8555`：
   
   ```yaml
   rtspAddress: :8555
   ```
   
   然後重新啟動 `mediamtx`。

---

### 3. 使用 FFmpeg 推送 RTSP 流

- **從攝像頭推送流**：
  
  > ffmpeg -f dshow -i video="USB2.0 FHD UVC WebCam" -c:v libx264 -preset fast -f rtsp rtsp://localhost:8555/stream
  
  ```bash
  ffmpeg -f dshow -i video="USB2.0 FHD UVC WebCam" -c:v libx264 -preset fast -f rtsp rtsp://localhost:8555/stream
  ```

- **使用 UDP:** 
  
  ```batch
  ffmpeg -f dshow -i video="USB2.0 FHD UVC WebCam" -c:v libx264 -preset ultrafast -tune zerolatency -f rtsp -rtsp_transport udp rtsp://localhost:8555/stream
  ```

- **從本地文件推送流**：
  
  ```bash
  ffmpeg -re -i input.mp4 -c:v libx264 -preset fast -f rtsp rtsp://localhost:8555/stream
  ```

這些命令將使用 H.264 編碼器將視頻流推送到 MediaMTX RTSP 伺服器。

---

### 4. 接收 RTSP 流

- **VLC 播放器**：
  
  1. 打開 VLC，選擇 `Media -> Open Network Stream`。
  
  2. 輸入 URL：
     
     ```
     rtsp://localhost:8555/stream
     ```
  
  3. 點擊 `Play` 來查看流。

- **FFplay**：
  你也可以使用 FFplay 來查看流：
  
  ```bash
  ffplay rtsp://localhost:8555/stream
  ```

---

### 5. 容器內的顯示問題

在 Docker 容器內使用 Python 或 OpenCV 時，可能會遇到無法顯示 GUI 界面的問題。這裡有幾種解決方法：

- **X11 轉發**：
  
  1. 啟動容器時添加 X11 轉發支持：
     
     ```bash
     xhost +local:docker
     docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix your_image
     ```

- **無頭模式**：如果不需要顯示 GUI，可以使用 OpenCV 的無頭版本，避免使用 `cv2.imshow()`，改用 `cv2.imwrite()` 保存幀：
  
  ```python
  import cv2
  cap = cv2.VideoCapture('rtsp://localhost:8555/stream')
  while True:
      ret, frame = cap.read()
      if not ret:
          break
      cv2.imwrite('output_frame.jpg', frame)
  cap.release()
  ```

---

### 6. 常見問題解決

- **端口佔用**：使用以下命令查找佔用端口的應用：
  
  ```bash
  netstat -ano | findstr :8554
  ```
  
  找到相應的進程後，可以選擇關閉或使用不同的端口。

- **解碼錯誤**：如果出現 H.264 解碼錯誤（如 `co located POCs unavailable`），這可能是網絡延遲引起的，通常可以忽略這些錯誤，除非影響播放。

---

### 總結

通過 FFmpeg 將視頻流推送到 MediaMTX RTSP 伺服器是一種簡單高效的本地或局域網內部署流媒體的方式。MediaMTX 作為 RTSP 伺服器接收並分發流，可以被 VLC、FFplay 或其他支持 RTSP 協議的客戶端接收播放。當在無頭環境（例如 Docker 容器）中運行時，可以使用 X11 轉發或無頭模式來處理顯示問題。

# 6. B camera 安裝 python 、python opencv、nano

```batch
apt-get update
apt-get install -y python3 python3-pip
```

```batch
apt-get install -y nano
```

```batch
pip3 install opencv-python
```

## 🪛安裝 opencv-python 發生錯誤 & 如何解決

```batch
root@326a7e8589f3:/# pip3 install opencv-python
error: externally-managed-environment

× This environment is externally managed
╰─> To install Python packages system-wide, try apt install
    python3-xyz, where xyz is the package you are trying to
    install.

    If you wish to install a non-Debian-packaged Python package,
    create a virtual environment using python3 -m venv path/to/venv.
    Then use path/to/venv/bin/python and path/to/venv/bin/pip. Make
    sure you have python3-full installed.

    If you wish to install a non-Debian packaged Python application,
    it may be easiest to use pipx install xyz, which will manage a
    virtual environment for you. Make sure you have pipx installed.

    See /usr/share/doc/python3.12/README.venv for more information.

note: If you believe this is a mistake, please contact your Python installation or OS distribution provider. You can override this, at the risk of breaking your Python installation or OS, by passing --break-system-packages.
hint: See PEP 668 for the detailed specification.
root@326a7e8589f3:/#
```

---

這個錯誤是因為當前的容器系統管理了 Python 環境，限制了您直接使用 `pip` 進行安裝。要繞過這個限制，您可以使用虛擬環境來安裝 OpenCV。

---

以下是步驟：

1. **安裝一下 utility:** 
   
   > debconf: delaying package configuration, since apt-utils is not installed
   
   是在安裝包時出現的一個常見提示，表示系統暫時推遲配置安裝包，因為缺少 **apt-utils** 工具。
   
   ```batch
   apt-get install -y apt-utils
   ```

2. **安裝並創建虛擬環境**：
   
   ```batch
   apt-get update
   apt-get install -y python3-venv
   ```
   
   ```batch
   python3 -m venv venv
   ```
   
   - [沒顯示文字，等上一段時間5~10min，然後執行 source venv....就行。]() 

3. **激活虛擬環境**：
   
   ```batch
   source venv/bin/activate
   ```

4. **使用虛擬環境中的 `pip` 安裝 OpenCV**：
   
   ```batch
   pip install opencv-python
   ```
   
   - [同上，又不顯示文字了...(?)，大約過30秒才出現]() 
   
   - 大約10min 內安裝完了。

5. 安裝 ffmpeg
   
   ```batch
   pip install ffmpeg-python
   ```
   
   - 繼續等待，1~2min。

6. **安裝完成後運行腳本**：
   在虛擬環境中運行您的 Python 腳本：
   
   ```batch
   cd home
   python rtsp_scripts/rtsp_receiver.py
   ```
   
   - [程式碼請看 7.]() 

## 🪛安裝 headless版本 避免無GUI而引發異常

1. 先移除剛剛安裝的 opencv-python

2. 先確認剛剛確實有安裝在 venv 虛擬環境
   
   > source venv/bin/activate ，進入虛擬環境
   > 
   > pip show opencv-python，查看版本
   
   ```batch
   source venv/bin/activate
   pip show opencv-python
   ```

3. 移除舊版本
   
   ```batch
   pip uninstall opencv-python
   ```

4. 安裝 headless 版本
   
   ```batch
   pip install opencv-python-headless
   ```

## 安裝 flask 讓畫面從 8554 出去讓windows以瀏覽器觀看

1. 安裝 flask ( 注意，跟上面一樣都是在虛擬機內)
   
   ```batch
   pip install flask
   ```

2. 

# 7. B camera 建立資料夾於 home 之下，放code。

> 建議將檔案放在 `/home` 或 `/root` 目錄中，這些目錄通常是用來存放用戶文件的，便於管理和訪問。

```batch
mkdir /home/rtsp_scripts
```

## [ 舊版本 ] rtsp_receiver.py

```batch
cd /home/rtsp_scripts
nano rtsp_receiver.py
```

```python
import cv2
import numpy as np
import ffmpeg

# 設置 RTSP 流 URL
rtsp_url = "rtsp://172.17.0.3:8555/stream"

# 創建視頻捕獲對象
cap = cv2.VideoCapture(rtsp_url)

# 檢查是否成功打開流
if not cap.isOpened():
    print("Error: 無法打開 RTSP 流")
    exit()

while True:
    # 從流中捕獲每一幀
    ret, frame = cap.read()
    if not ret:
        print("Error: 無法讀取幀")
        break

    # 顯示幀
    cv2.imshow('RTSP Stream', frame)

    # 按 'q' 鍵退出
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# 釋放資源
cap.release()
cv2.destroyAllWindows()
```

## [ flask 版本 ] rtsp_flask.py

撰寫 rtsp_flask.py

```batch
cd /home/rtsp_scripts
nano rtsp_flask.py
```

```py
from flask import Flask, Response
import cv2

app = Flask(__name__)

# 設置 RTSP 流 URL
rtsp_url = "rtsp://192.168.100.110:8555/stream"  # 將 <windows-ip> 替換為實際的 Windows IP 地址

def generate_frames():
    # 打開 RTSP 流
    cap = cv2.VideoCapture(rtsp_url)

    # 檢查是否成功打開流
    if not cap.isOpened():
        print("Error: 無法打開 RTSP 流")
        return

    while True:
        # 從流中捕獲每一幀
        success, frame = cap.read()
        if not success:
            break
        else:
            # 將幀編碼為 JPEG
            ret, buffer = cv2.imencode('.jpg', frame)
            if not ret:
                continue

            # 將幀轉換為字節流
            frame = buffer.tobytes()
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')

@app.route('/video_feed')
def video_feed():
    # 返回視頻流的響應
    return Response(generate_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')

if __name__ == "__main__":
    # 運行 Flask 伺服器，並綁定到所有可用網絡接口
    app.run(host='0.0.0.0', port=8554)
```

# 8. nano 操作教學

這些快捷鍵主要是用於 **nano 編輯器**，符號 `^` 代表按下 **Ctrl** 鍵，同時按下對應字母鍵。例如：

- `^G` = 按下 **Ctrl + G**：顯示幫助。
- `^X` = 按下 **Ctrl + X**：退出編輯器。
- `^O` = 按下 **Ctrl + O**：保存當前文件（稱為 "Write Out"）。
- `^K` = 按下 **Ctrl + K**：剪切當前行。
- `^U` = 按下 **Ctrl + U**：粘貼。

其他的快捷鍵也都以 **Ctrl** 開頭，比如 `^W` 是查找，`^C` 是顯示當前光標位置。

---

`M-U` 和 `M-A` 中的 `M` 表示 **Alt** 鍵，這些快捷鍵的操作方法如下：

- **M-U** = 按下 **Alt + U**：執行撤銷操作（Undo）。
- **M-A** = 按下 **Alt + A**：設置標記（Set Mark），用於選擇文本。

在不同的鍵盤布局上，**Alt** 鍵有時候可能需要換成 **Esc** 鍵來配合使用。例如：

- 先按下 **Esc**，然後再按下 `U` 鍵以執行撤銷操作。

# 9. 設定系統編碼: UTF8

您遇到這個錯誤是因為在當前容器中缺少相應的語言環境數據包。要解決這個問題，您可以按照以下步驟1~4來安裝 **en_US.UTF-8** 語言包：

1. **更新包列表**：
   
   > apt-get update

2. **安裝 locale 語言支持包**：
   
   ```batch
   apt-get install -y locales
   ```

3. **生成 zh_TW.UTF-8 語言環境**：
   
   ```batch
   locale-gen zh_TW.UTF-8
   ```

4. **設置語言環境**：
   
   ```batch
   export LANG=zh_TW.UTF-8
   export LC_ALL=zh_TW.UTF-8
   ```
   
   弄到這邊應該進去，再度複製貼上就能看到中文了。

5. **查看語言環境:** 
   
   ```batch
   locale
   ```

# 如何進入 docker bash 介面

`印出所有` : 

> docker ps -a

```batch
docker ps -a
```

`啟動` :

> docker start B-Camera-pi-ubuntu

```batch
docker start B-Camera-pi-ubuntu
```

`進入`: 

> docker exec -it B-Camera-pi-ubuntu /bin/bash

```batch
docker exec -it B-Camera-pi-ubuntu /bin/bash
```

# 提示 關閉自動追蹤聚焦人臉的功能

- 我試著用 ffmplay 卻發現 視野跟 直接使用相機 (windows內建) 的不同，全都聚焦在
  
  我臉上，因此去找方法，最後發現從 windows 可以關閉。

- 藍芽與裝置 > 攝影機 > USB2.0 FHD UVC WebCam 
  
  下面有一個自動構圖處理，關閉即可。

![](../../../Images/2024-10-12-16-33-07-image.webp)
