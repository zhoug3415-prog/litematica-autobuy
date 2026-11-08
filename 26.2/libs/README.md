# 本地 Printer 依賴（選用）

本模組對 Litematica Printer 的整合是反射式，編譯不需要 Printer jar。
若要在開發環境 `runClient` 中一起載入 Printer，請把下列 jar 放進此目錄：

- 檔名：`litematica-printer-1.1.3+26.2.jar`
- 來源：https://github.com/water2004/litematica-printer/releases/tag/v1.1.3
- SHA-256：請以 release 頁面的 26.2 asset digest 為準

Gradle 偵測到檔案存在時會以 `modLocalRuntime` 載入；檔案不會被打包進本模組的 jar。
正式遊玩時請把這個 Printer jar 直接放進遊戲的 `mods/`。
