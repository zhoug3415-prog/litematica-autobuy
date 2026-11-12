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
| Cloth Config | 26.2.155 |
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
2. 放入 Fabric API `0.161.0+26.2`、Litematica `0.28.8`、MaLiLib `0.29.6`、Cloth Config `26.2.155`。
3. 放入 water2004 Printer `1.1.3+26.2`（要用 Printer gate 時必要）。
4. 放入本專案 `litematica-autobuy-1.0.0+26.2.jar`。
5. 啟動後在 **控制 → 按鍵綁定 → Litematica AutoBuy** 綁定六個快捷鍵（預設未綁定）。

## 功能

- 讀取手動選取 placement（或自適應模式的最近藍圖）的材料清單，比對主背包＋副手的缺額。
- 只購買白名單內且缺額 > 0 的 item；`/buy <item_id> <count>` 不帶 `minecraft:`。
- 庫存門檻：`>=4 組` 不買、`1–4 組` 等待、`<=1 組` 買到 4 組（不超過缺額）。
- 購買模式快捷鍵切換：精確單層（目前單層未放置材料清單 − 身上物品，忽略 4/1 門檻）／大量（整份投影＋4/1 門檻）。
- 精確單層只支援 Litematica「層級：單層」；其他 Layer 模式暫停。使用 `countMissing − countMismatched`，只計世界為空氣的未放置位置，正確放置、錯方塊及錯狀態都不計。
- 每輪批次重新計數；換層／軸向／placement 時丟棄舊清單與未送出佇列，不反覆重啟計算中的任務，完成的空層視為無缺料。
- 切換模式會把 Litematica 材料清單類型改成渲染層／全部並重新計數；計算中會拒絕切換。
- 背包容量等待：本輪批次＋待送達需要的主背包格數不足時暫停，空格／同物品部分堆疊足夠後自動恢復；需求 >36 格會提示降低上限。
- 自適應白名單（`autoSyncWhitelist=true`）：自動以玩家到已啟用子區域的最短距離挑選最近藍圖，白名單整份替換；非本選區及尚需放置數為 0 的項目刪除。精確單層只取本層未放置材料，大量模式取整份藍圖。
- 關閉自動補料／Printer 時仍可自動同步但不購買；名單計算中或失敗時保留原名單並停止新購買，換目標先清除舊佇列。
- 白名單同步快捷鍵也使用整份替換；關閉自適應時來源為 Litematica 手動選取的 placement。
- Cloth Config 設定 GUI 快捷鍵：自動補料開關、購買模式、單次上限、冷卻、庫存門檻、白名單等；儲存會寫回 JSON 並套用。
- 冷卻、每輪指令數、單次上限、待送達逾時／重試、Printer gate 等安全設定與主專案相同。
- 材料清單未完成時只提示一次，不會自動刷新打斷 Litematica；action bar 顯示百分比、剩餘區塊與 ETA，停滯時提示載入區塊，完成後提示已就緒。
- 設定檔：該實例的 `config/litematica-autobuy.json`（首次啟動自動建立）。
- 預設白名單空白＝全部禁止。

詳細功能、設定表、限制與測試清單請參考上層主專案的 `README.md`；本目錄的設定檔與程式已包含最新的白名單同步、購買模式、容量等待、進度 ETA 與 Cloth Config GUI。

## 驗證狀態

- 2026-11-12：`./gradlew -p 26.2 build`：**BUILD SUCCESSFUL in 14s**（從主專案執行；Fabric Loom 1.19.4；JDK 25；自適應白名單與 Printer 佇列閘門更新）。
- 依賴解析：Fabric API 0.161.0+26.2、Litematica 0.28.8、MaLiLib 0.29.6、Cloth Config 26.2.155。
- 產物：`build/libs/litematica-autobuy-1.0.0+26.2.jar`（約 85 KB，SHA-256 `b6fc0f0d9168cfd5848bcaba8e1fe42e443f8f3605ec8c78935759f141a2f8d0`）。
- 兩版相關 Java 實作逐檔一致；以實際原始碼搭配外部 API 替身執行的 134 項隔離回歸測試，在 JDK 25／27 均通過（各 1,146 個斷言），不等同遊戲實測。
- 已檢查 `fabric.mod.json`（minecraft 26.2、依賴版本）、client entrypoint、`zh_cn`／`zh_tw`／`en_us` 語系與 class 內容；沒有 Mixin，也沒有打包 Litematica／MaLiLib／Printer。
- **尚未**啟動 26.2 遊戲、載入世界、選取 placement、連線伺服器或驗證 `/buy`；上述需在 26.2 環境手動測試。

## 授權

本專案採用 MIT License，完整條文見上層根目錄的 `LICENSE`。Litematica、MaLiLib、Litematica Printer 為獨立第三方專案，依其各自授權使用。
