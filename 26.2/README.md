# Litematica AutoBuy — Minecraft 26.2 版

這是主專案（26.3）的 **26.2 獨立變體**，建置與依賴都和 26.3 分開，請不要和 26.3 的 jar 混用。

## 版本

| 項目 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.20.1+（build 使用 0.20.1） |
| Fabric API | 0.161.0+26.2 |
| Litematica | 0.28.8 |
| MaLiLib | 0.29.6 |
| Litematica Printer | water2004 `1.1.3+26.2` |
| Java | 25 |
| Gradle / Loom | 9.8.2 / 1.19-SNAPSHOT（實際解析 1.19.4） |

26.2 也使用官方 Mojang mappings，不使用 Yarn。26.2 的快捷鍵使用 Fabric `KeyMappingHelper`＋`InputConstants.Type.KEYSYM`／GLFW 鍵碼（26.3 才是 SDL scancode）。

## 建置

需求：JDK 25+、可連 Fabric／Modrinth Maven。

```bash
./gradlew build
```

輸出：

```
build/libs/litematica-autobuy-1.0.0+26.2.jar
```

## 安裝

1. 建立 Minecraft 26.2 的 Fabric 實例（Loader 0.20.1+）。
2. 放入 Fabric API `0.161.0+26.2`、Litematica `0.28.8`、MaLiLib `0.29.6`。
3. 放入 water2004 Printer `1.1.3+26.2`（要用 Printer gate 時必要）。
4. 放入本專案 `litematica-autobuy-1.0.0+26.2.jar`。
5. 啟動後在 **控制 → 按鍵綁定 → Litematica AutoBuy** 綁定四個快捷鍵（預設未綁定）。

## 功能

- 讀取目前選取 placement 的完整材料清單，比對主背包＋副手的缺額。
- 只購買白名單內且缺額 > 0 的 item；`/buy <item_id> <count>` 不帶 `minecraft:`。
- 庫存門檻：`>=4 組` 不買、`1–4 組` 等待、`<=1 組` 買到 4 組（不超過缺額）。
- 購買模式快捷鍵切換：精確渲染層（渲染層材料清單、本層缺多少買多少、忽略 4/1 門檻）／大量（整份投影＋4/1 門檻）。
- 精確層支援 Litematica 的 Layer 模式：下方所有、上方所有、單層；使用 `countMissing`，正確放置不買，錯狀態仍算。
- 切換模式會把 Litematica 材料清單類型改成渲染層／全部並重新計數；計算中會拒絕切換。
- 背包容量等待：本輪批次＋待送達需要的主背包格數不足時暫停，空格／同物品部分堆疊足夠後自動恢復；需求 >36 格會提示降低上限。
- 白名單同步快捷鍵：`missing > 0` 加入、`missing == 0` 移除。
- 冷卻、每輪指令數、單次上限、待送達逾時／重試、Printer gate 等安全設定與主專案相同。
- 材料清單未完成時只提示一次，不會自動刷新打斷 Litematica；action bar 顯示百分比、剩餘區塊與 ETA，停滯時提示載入區塊，完成後提示已就緒。
- 設定檔：該實例的 `config/litematica-autobuy.json`（首次啟動自動建立）。
- 預設白名單空白＝全部禁止。

詳細功能、設定表、限制與測試清單請參考上層主專案的 `README.md`；本目錄的設定檔與程式已包含最新的白名單同步、庫存門檻與空清單修正。

## 驗證狀態

- `./gradlew clean build`：**BUILD SUCCESSFUL in 5s**（Fabric Loom 1.19.4；MIT metadata 重建）。
- 依賴解析：Fabric API 0.161.0+26.2、Litematica 0.28.8、MaLiLib 0.29.6。
- 產物：`build/libs/litematica-autobuy-1.0.0+26.2.jar`（約 67 KB，SHA-256 `e7640dfe52d15f9bce36fa1d9b1c8b9e09e5cc2366183fe1da59412ff80dd990`）。
- 已檢查 `fabric.mod.json`（minecraft 26.2、依賴版本）、client entrypoint、`zh_cn`／`zh_tw`／`en_us` 語系與 class 內容；沒有 Mixin，也沒有打包 Litematica／MaLiLib／Printer。
- **尚未**啟動 26.2 遊戲、載入世界、選取 placement、連線伺服器或驗證 `/buy`；上述需在 26.2 環境手動測試。

## 授權

本專案採用 MIT License，完整條文見上層根目錄的 `LICENSE`。Litematica、MaLiLib、Litematica Printer 為獨立第三方專案，依其各自授權使用。
