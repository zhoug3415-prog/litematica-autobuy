# Litematica AutoBuy

Minecraft Java Edition 26.3 / Fabric 的**客戶端**模組。讀取目前選取的 Litematica placement 材料清單，比對玩家主背包＋副手，對缺料物品送出伺服器商店的 `/buy <item_id> <count>`。

> 本模組只負責「缺料偵測＋購買指令」。它不會繞過伺服器經濟、權限或冷卻，不會修改 Litematica 投影或 Printer 的放置邏輯，也不會自動開啟聊天 GUI。

---

## 1. 已確認的需求

- 遊戲版本：Minecraft Java Edition 26.3（Wilderness Bound）
- 載入器：Fabric
- Printer 分支：water2004 的 **Litematica Printer 4th v1.1.3**，26.3 版 jar 為 `1.1.3-hotfix.1+26.3`
- 觸發方式：持續自動＋立即掃描，兩者都有快捷鍵
- 庫存範圍：主背包 36 格＋副手；不計盔甲、終界箱、附近容器、潛影盒／束口袋內容
- 缺料基準：預設 `missing`（尚未放置的方塊）；可改 `total`
- 同 item ID 的不同 NBT／components：合併為同一種物品
- 投影範圍：預設使用手動選取的 placement；自適應白名單開啟時使用離玩家最近的已啟用 placement
- 白名單空值：**全部禁止**
- 白名單同步：以目標 placement 尚需材料整份替換白名單，刪除其他選區物品及已完成項目；可手動同步或啟用 `autoSyncWhitelist` 自適應同步
- 商店指令：`/buy <item_id> <count>`；`item_id` 不帶 `minecraft:` 前綴；投影 ID 直接等於商店 ID
- 購買限制：指令冷卻 1000 ms、每輪最多 4 條指令、單次購買上限 576
- 購買前確認：不需要，直接送出
- 缺料歸零：停止送單，但持續監控
- 庫存門檻：白名單物品庫存 `>= 4 組` 不買、`1–4 組` 等待、`<= 1 組` 才買；`<=1 組` 時補到 4 組，但不超過投影缺額（組＝該物品最大堆疊數）
- 購買模式可切換：精確單層（本層缺多少買多少）／大量（整份投影＋4/1 庫存門檻）；新快捷鍵切換
- Printer gate 預設：Printer 的全域 Work Switch 與 Print 模組都開啟

---

## 2. 版本與 mappings（重要）

已核對 26.3 的官方 Fabric 範例與 water2004 Printer 26.3 原始碼：

| 項目 | 使用版本 |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1（mod metadata 最低 0.19.5） |
| Fabric API | 0.165.2+26.3（實際最新；任務文件中的 0.160.7 為較舊版本） |
| Litematica | 0.29.1 |
| MaLiLib | 0.30.2 |
| Cloth Config | 26.3.159 |
| Litematica Printer 4th | water2004 `1.1.3-hotfix.1+26.3` |
| Java | 25 |
| Gradle | 9.8.2 |
| Fabric Loom | 1.19-SNAPSHOT（實際解析 1.19.4） |

**不使用 Yarn mappings。** 26.3 的 Fabric 範例與 Printer 26.3 原始碼都使用官方 Mojang mappings；本專案統一使用官方 mappings，不混用。

26.3 的 Fabric API 已沒有舊的 `fabric-key-binding-api-v1`／`KeyBindingHelper`，改由 `fabric-key-mapping-api-v1` 的 `KeyMappingHelper` 與 SDL scancode 處理快捷鍵。本模組使用新的 API。

### 依賴下載

- Fabric Loader / Fabric API：<https://fabricmc.net/develop/>
- Fabric API 26.3：<https://modrinth.com/mod/fabric-api/versions?g=26.3>
- Litematica 0.29.1：<https://modrinth.com/mod/litematica>
- MaLiLib 0.30.2：<https://modrinth.com/mod/malilib>
- Cloth Config 26.3.159：<https://modrinth.com/mod/cloth-config>
- Litematica Printer 4th v1.1.3（water2004）：<https://github.com/water2004/litematica-printer/releases/tag/v1.1.3>
  - 26.3 檔案：`litematica-printer-1.1.3-hotfix.1+26.3.jar`

---

## 3. 安裝

1. 安裝 Minecraft 26.3 與 Fabric Loader 0.20.1+。
2. 將下列 jar 放入遊戲的 `mods/`：
   - Fabric API `0.165.2+26.3`
   - Litematica `litematica-fabric-26.3-0.29.1.jar`
   - MaLiLib `malilib-fabric-26.3-0.30.2.jar`
   - Cloth Config `cloth-config-fabric-26.3.159.jar`
   - Litematica Printer `litematica-printer-1.1.3-hotfix.1+26.3.jar`
   - 本模組 `litematica-autobuy-1.0.0.jar`
3. 啟動遊戲，進入 **選項 → 控制 → 按鍵綁定**，找到 **Litematica AutoBuy**（中文語系顯示 **Litematica 自动补料／自動補料**）分類。
4. 依需求綁定六個快捷鍵（預設皆未綁定，避免與其他模組衝突）。
5. 在 Litematica 選取一個 placement，讓它的材料清單計算完成。
6. 白名單可用「同步白名單」快捷鍵從目前選取 placement 產生；也可直接編輯 `config/litematica-autobuy.json`。編輯後按重新載入快捷鍵或重開遊戲。
7. 按「立即掃描並補料」測試，或開啟「切換自動補料」。

> 預設 Printer gate 需要 Printer 的 Work Switch＋Print 模組都開啟。若只想單獨測試購買功能，可把 `printerGateMode` 改成 `ALWAYS`。

---

## 4. 快捷鍵

預設全部未綁定，請自行在控制設定中綁定：

| Key mapping ID | 功能 |
| --- | --- |
| `key.litematica-autobuy.toggle` | 切換持續自動補料 |
| `key.litematica-autobuy.scan` | 立即掃描並補料（即使持續自動關閉也可用） |
| `key.litematica-autobuy.reload` | 重新載入 `config/litematica-autobuy.json` |
| `key.litematica-autobuy.sync_whitelist` | 從目前選取 placement 同步白名單（`missing > 0` 加入、`missing == 0` 移除） |
| `key.litematica-autobuy.purchase_mode` | 切換購買模式：精確單層 ↔ 大量 |
| `key.litematica-autobuy.config_gui` | 開啟 AutoBuy 設定 GUI（Cloth Config） |

- 立即掃描仍受 Printer gate、白名單、冷卻、上限等安全機制限制。
- 重新載入會解除「購買失敗封鎖」，但不會清除已送出、尚未到帳的待追蹤訂單。
- 切換關閉會清除尚未送出的佇列；已送出的訂單仍會追蹤到逾時為止。

### 設定 GUI

- 新快捷鍵 `key.litematica-autobuy.config_gui`（預設未綁定）開啟 Cloth Config 設定畫面。
- 需安裝 Cloth Config 26.3.159+。
- 可設定：自動補料開關、購買模式、單次購買上限、每輪指令數、冷卻、掃描間隔、待送達逾時／重試、庫存門檻（停止／觸發／目標組數）、副手計入、自適應白名單（最近藍圖）、GUI／容量暫停、聊天／action bar／購買公告／debug、Printer gate、需求計數、白名單與失敗訊息樣式。
- 按 Done／儲存會寫回 `config/litematica-autobuy.json` 並即時套用；模式切換會同步 Litematica 材料清單類型並重新計數。
- 若模式切換失敗（材料清單正在計算或未選取 placement），其他設定仍會儲存，聊天會提示。

---

## 5. 設定檔

路徑：`config/litematica-autobuy.json`（相對於遊戲目錄）。

首次啟動若檔案不存在，模組會自動建立預設檔。專案根目錄的 `config/litematica-autobuy.json` 是同一份預設參考。

| 欄位 | 預設 | 說明 |
| --- | --- | --- |
| `scanIntervalTicks` | `40` | 持續模式的掃描間隔（20 tick ≈ 1 秒） |
| `printerGateMode` | `PRINTER_ENABLED` | `ALWAYS`、`PRINTER_ENABLED`、`PRINTER_PRINTING` |
| `requiredCountMode` | `MISSING` | `MISSING` 使用 `getCountMissing()`；`TOTAL` 使用 `getCountTotal()` |
| `countOffhand` | `true` | 是否把副手計入庫存 |
| `whitelist` | `[]` | 允許購買的物品 ID；空值代表全部禁止 |
| `whitelistEmptyAllowAll` | `false` | 是否讓空白名單變成全部允許（不建議） |
| `autoSyncWhitelist` | `false` | 自適應白名單：自動追蹤最近已啟用藍圖，以本模式尚需材料替換名單，移除非本選區項目；自動購買關閉時仍同步 |
| `stockGateEnabled` | `true` | 是否啟用庫存門檻 |
| `stockHighStacks` | `4` | 庫存 >= 此組數不買 |
| `stockLowStacks` | `1` | 庫存 <= 此組數才買 |
| `stockTargetStacks` | `4` | 觸發時目標組數；仍受投影缺額與單次上限 |
| `purchaseMode` | `BULK` | 購買模式：`EXACT_LAYER`（精確單層）或 `BULK`（整份投影＋4/1 門檻） |
| `commandCooldownMs` | `1000` | 兩條 `/buy` 指令的最小間隔 |
| `maxCommandsPerScan` | `4` | 每次掃描最多排入幾條指令 |
| `maxItemsPerOrder` | `576` | 單次 `/buy` 的數量上限 |
| `pendingTimeoutSeconds` | `30` | 已下單待送達的逾時秒數 |
| `pendingMaxRetries` | `2` | 逾時後最多再重試幾次（不含第一次） |
| `skipWhenScreenOpen` | `true` | 有 GUI 開啟時暫停掃描與送出 |
| `skipWhenInventoryFull` | `true` | 背包容量不足時暫停；等主背包空格＋同物品部分堆疊空間可容納本輪批次＋待送達後自動恢復 |
| `chatFeedback` | `true` | 是否在聊天欄顯示回饋 |
| `actionBarSummary` | `true` | 是否在 action bar 顯示掃描摘要 |
| `announcePurchases` | `true` | 是否在聊天欄逐條顯示已送出的 `/buy` |
| `debugLog` | `false` | 是否輸出 debug 日誌到 `logs/latest.log` |
| `failurePatterns` | 見預設檔 | 伺服器失敗訊息樣式（不分大小寫、子字串比對） |

### 白名單正規化

- `minecraft:stone` 與 `stone` 都會正規化成 `stone`。
- 其他命名空間會保留，例如 `create:andesite`。
- 空白、含空格或格式不正確的項目會被忽略並記錄警告。
- 名稱必須是物品 ID，不支援物品 tag 或顯示名稱。

### 為什麼空白名單是全部禁止

這是已確認的安全預設。若你確定要讓空白名單代表「全部允許」，把 `whitelistEmptyAllowAll` 設為 `true`，但這可能讓任何投影缺料都被購買。

---

## 6. 運作方式

1. 每輪掃描先確認：不在創造／旁觀模式、沒有開啟 GUI、Printer gate 通過、目前已選取 placement。
2. 取得 `SchematicPlacement#getMaterialList()`，讀取 `MaterialListBase#getMaterialsAll()`。
3. 精確單層取 `max(0, countMissing − countMismatched)`；大量模式依 `requiredCountMode` 取 `countMissing` 或 `countTotal`，用 item ID 合併 components。
4. 統計主背包 36 格＋副手的物品數量，計算 `max(0, required - available)`。
5. 過濾白名單、待送達、失敗封鎖、每輪上限後，排入購買佇列。
6. 佇列每次最多送一條指令，遵守 `commandCooldownMs`；單次數量不超過 `maxItemsPerOrder`。
7. 送出後記錄「已下單待送達」：以送出當下的庫存為基準，庫存增加即視為到帳；逾時才允許重試，重試次數用完後封鎖該物品。
8. 聊天欄收到符合 `failurePatterns` 的伺服器訊息時，停止該物品並提示；權限錯誤會額外暫停送出 60 秒。

材料清單是 Litematica 的非同步工作，**尚未計算完成時會是空清單**；本模組會安全跳過並提示 placement 名稱。每次「空清單」狀態只提示一次，不會在計算中反覆刷新（避免打斷 Litematica）；action bar 會顯示計算百分比、剩餘區塊與 ETA。若 5 秒沒有新進度，會提示「等待區塊載入」，請靠近 placement 或讓該區域的區塊載入。完成後會提示「材料清單已就緒」。計算速度由 Litematica 控制，若範圍很大請耐心等待或縮小選取範圍。

### 白名單同步

- 快捷鍵：`key.litematica-autobuy.sync_whitelist`（預設未綁定）。
- 來源：目前選取 placement 的 `MaterialListBase#getMaterialsAll()`；大量模式用 `countMissing`，精確單層用 `max(0, countMissing − countMismatched)`；同 item ID 的不同 components 會合併。
- 尚需放置數 > 0 的物品加入白名單；等於 0 的物品從白名單移除（不先扣除庫存）。
- 同步是**整份替換**：不在目前材料清單中的舊項目一律移除；已完成（尚需放置數為 0）的項目也移除。
- 只改寫 `config/litematica-autobuy.json` 的 `whitelist` 欄位，其他設定保留；若 config 格式錯誤或無法寫入，白名單不變並提示。
- 同步以目前記憶體中的白名單為基礎；若你手動改過 JSON（尤其 `whitelist`），請先按重新載入再同步，否則同步可能覆蓋尚未載入的變更。
- 同步本身不送出任何 `/buy`。手動同步成功後會把自動掃描延後一輪，避免同步完立刻下單；若白名單有變更，尚未送出的購買佇列會被清除，下一輪重新評估。
- `autoSyncWhitelist = true` 啟用**自適應白名單**（GUI → 白名單／錯誤樣式，預設關閉）。依玩家到各 placement 已啟用子區域的最短距離挑選最近藍圖；停用或沒有有效子區域的 placement 不參與，等距時保留目前目標以避免反覆切換。
- 白名單與補料使用同一個最近目標，不改動 Litematica 的手動選取或 Printer 放置邏輯。精確單層取目前層的未放置材料；大量模式取整份 placement 的缺料。
- 自適應同步依 `scanIntervalTicks` 執行，關閉自動補料、Printer 未啟用或創造模式時仍會更新白名單，**但不會因此開啟購買**。購買仍須自動補料開啟或手動掃描，並通過原有安全條件；GUI 開啟時依 `skipWhenScreenOpen` 暫停自動掃描。
- 換最近目標、移動／旋轉 placement 或換單層時，立即清除未送出的舊佇列，等待新計數；只取消本模組建立的過期計數任務，不中止 Litematica 或其他模組的任務。
- 名單變更的當輪不購買，下一輪重新計算後使用新名單；沒有目標、未完成或失敗時保留原名單並禁止以它對新目標下單。完成的空清單則安全清空白名單；寫入失敗時不購買。
- 手動同步不受 Printer gate、GUI、創造模式限制，但仍需要已選取 placement 且材料清單已完成。
- 若 Litematica 材料清單目前只包含渲染層，同步也只處理該範圍。
- 自適應模式下，空白名單永遠禁止全部物品，即使 `whitelistEmptyAllowAll=true` 也不例外；關閉自適應才沿用該選項。

### 庫存門檻

- 套用到所有白名單物品；庫存沿用 `countOffhand` 的主背包＋副手統計。
- 「1 組」是該物品的最大堆疊數；方塊通常是 64，16/1 堆疊物品按自身數值。
- `available >= stockHighStacks × stackSize`：不買。
- `available <= stockLowStacks × stackSize`：買；購買量 = `min(stockTargetStacks × stackSize - available, 投影缺額)`，最後仍受 `maxItemsPerOrder` 限制。
- 介於兩者（例如 2～3 組）：不買，形成滯迴，等降到 <=1 組才再次觸發。
- 例：黑曜石庫存 0 → 目標 4 組，但因單次上限/缺額限制可能只排 256；庫存 2 組 → 不買；庫存 >=4 組 → 不買；Printer 消耗到 <=1 組 → 再補。
- 若 `maxItemsPerOrder` 太小，一次補不到 4 組，會等庫存再降到 <=1 組才再買。
- `stockGateEnabled=false` 可恢復原本「只要投影缺額 > 0 就買」的行為。
- `purchaseMode=EXACT_LAYER` 時不套用庫存門檻，改為精確購買本渲染層缺額；`BULK` 才套用 4/1 門檻。兩者都仍受白名單、冷卻、每輪指令數、單次 `maxItemsPerOrder`、待送達與失敗封鎖限制。

### 購買模式：精確層 / 大量

- 切換：新快捷鍵 `key.litematica-autobuy.purchase_mode`（預設未綁定），或 config 的 `purchaseMode`（`EXACT_LAYER` / `BULK`）。快捷鍵切換本次遊戲；config 是啟動預設值。
- 快捷鍵切換時，模組會把 Litematica 材料清單類型改成對應值：
  - 精確層 → **渲染層**
  - 大量 → **全部**
  - 切換會清空舊材料清單並重新計數；若目前有材料清單任務在計算，會拒絕切換，等進度／ETA 完成後再按。
  - 掃描時若模式與材料清單類型不符，且沒有計算任務，會自動切換並重算。
- **精確層（EXACT_LAYER）**
  - **只支援「層級：單層」**；下方所有、上方所有、範圍或全部模式會暫停購買／白名單同步並提示。本模組不會替你改 Layer 模式或層數。
  - 每輪批次重新建立目前單層的未放置材料清單：`max(0, countMissing − countMismatched)`，只計算尚未放置（世界為空氣）的位置；正確放置、錯方塊及錯狀態都不計。
  - 購買量 = `max(0, 本層未放置材料清單 − 主背包／副手庫存)`；副手依 `countOffhand`，忽略 4/1 庫存門檻及 `requiredCountMode`。
  - 切換層數、軸向、Layer 模式或選取 placement 後，舊清單不會用於新一輪購買；尚未送出的舊佇列會清除。計算中的任務不會反覆重啟。
  - 已完成的空層視為無缺料；正常重新計數只更新 action bar，不反覆發送準備／完成聊天訊息。
  - 手動掃描／同步只需按一次，計數完成後接續處理；等待期間若換層或修改 placement，取消該次手動操作。
  - 摘要會顯示單層軸向與層數，例如「精確層（單層 Y=64）」。
- **大量（BULK）**
  - 使用整份投影材料清單（全部），套用 4/1 庫存門檻，與原本大量補貨行為相同。
  - 同樣使用 `countMissing`：已正確放置不計，狀態錯誤仍算。
  - 摘要會顯示「大量」。
- 兩種模式都只買白名單內物品；白名單同步會使用目前模式的材料清單。
- 兩者都受 `maxItemsPerOrder`、每輪指令數、冷卻、待送達與背包容量等待限制。

### 背包容量等待

- 啟用：`skipWhenInventoryFull: true`（預設）。
- 「所有物品」的範圍＝**本輪計畫購買的批次**（受白名單、購買模式、`maxCommandsPerScan`、`maxItemsPerOrder`）**＋已送出待送達**。
- 可用容量＝主背包空格＋現有同物品部分堆疊的剩餘空間；副手與盔甲不計入容量。
- 容量不足時不送出，清除尚未送出的佇列，聊天／action bar 顯示需要的格數與目前空格；auto 掃描會持續重試，空間足夠後自動恢復購買。
- 如果需要的格數超過主背包總格數（36），會提示你降低 `maxItemsPerOrder` 或 `maxCommandsPerScan`，不會無限等待。
- 精確單層模式同樣會經過容量等待；`skipWhenInventoryFull=false` 才會忽略容量直接送單（可能掉地或被伺服器拒收）。

---

## 7. 邊界情況與安全行為

| 情況 | 行為 |
| --- | --- |
| 未選取 placement | 跳過並提示（聊天訊息有節流） |
| 材料清單尚未完成／為空 | 跳過並提示 placement；action bar 顯示百分比／剩餘區塊／ETA；停滯 5 秒提示載入區塊；不自動刷新；完成後提示已就緒 |
| 購買模式與材料清單類型不符 | 自動切換 Litematica 材料清單類型並重算；若有計數任務正在跑則等待，不購買 |
| 創造／旁觀模式 | 不掃描、不送指令 |
| 有 GUI 開啟 | 暫停掃描與送出；關閉 GUI 後恢復 |
| 背包容量不足 | 暫停購買；等主背包空格＋同物品部分堆疊空間足夠容納本輪批次＋待送達後自動恢復；需求 >36 格會提示降低上限 |
| 空白白名單 | 全部禁止；仍會顯示缺料摘要與被阻擋項目 |
| Printer 未安裝 | 預設 gate 安全跳過；可改用 `ALWAYS` |
| Printer API 不相容 | 反射失敗、顯示訊息、安全停用 gate；不會崩潰 |
| Printer 未啟用 | 預設 gate 跳過並提示 |
| `PRINTER_PRINTING` 模式但 Printer 閒置 | 跳過並提示 |
| `/buy` 送出例外 | 記錄日誌，不記錄為待送達；下一輪重新評估 |
| 伺服器回報購買失敗 | 封鎖該物品、清除佇列、聊天提示 |
| 權限不足 | 封鎖目標物品、暫停送出 60 秒、提示 |
| 物品逾時未到帳 | 30 秒後允許重試，最多 2 次；之後封鎖直到重新載入 |
| 世界／連線變更 | 自動關閉持續補料、清除待追蹤與封鎖狀態 |
| 連續未預期錯誤 | 記錄日誌，連續 5 次後安全停用自動補料 |

本模組不繞過伺服器經濟、權限或購買上限；伺服器若拒絕，會依錯誤訊息或逾時機制停止重送。

---

## 8. 已知限制

- 只支援 `/buy <item_id> <count>`；沒有稅金、每日上限、GUI 選單或特殊 ID 對照的處理。若你的伺服器格式不同，需修改 `BuyCommandSender`。
- 投影物品 ID 必須直接等於商店 ID；本模組不做商店別名對照。
- 材料需求來自客戶端 Litematica；可能與伺服器世界或商店庫存不同步。
- `countMissing` 由 Litematica 比較客戶端世界與投影得出；已放置、錯誤狀態、伺服器延遲都可能影響結果。
- 若 Litematica 材料清單設定為「渲染層」，本模組只會看到該範圍，摘要會標示「僅渲染層」。需要整份投影請把 Litematica 材料清單切回全部。
- 只計主背包＋副手；不計盔甲、終界箱、附近容器、潛影盒／束口袋內容。
- 同 item ID 的不同 components 會合併，因此無法區分附魔、容器內容等差異。
- 待送達判定只看該物品在背包中的數量是否增加；Printer 同時消耗材料、玩家手動移動物品、或伺服器延遲到帳，都可能造成誤判。30 秒逾時＋重試上限是為了避免無限重送，但不保證完全精準。
- 失敗訊息依 `failurePatterns` 子字串比對；不同伺服器外掛的訊息可能不同，請自行補充。
- 沒有圖形設定介面；請編輯 JSON 並按重新載入。
- 客戶端 only；伺服器必須支援 `/buy`，且你必須有權限。
- 本模組不啟用 Printer、不改變 Litematica 手動選取，也不修改放置邏輯；會透過 Litematica 計數任務建立補料目標的材料清單。
- 沒有使用 Mixin；相容性依賴 26.3 的公開 API 與 Printer 的反射入口。
- Litematica 材料清單 GUI 的 `multiplier` 不套用；本模組使用單份 placement 的 `countMissing`／`countTotal`。
- `PRINTER_PRINTING` 模式只在 `PrinterStatus.PRINTING` 時運作；Printer 因缺料停下來等待時可能不是 `PRINTING`，因此不建議用它來補料，預設的 `PRINTER_ENABLED` 較適合。
- Litematica 材料清單若包含實體 pick item（如物品展示框），也會被視為材料；請用白名單控制。
- 白名單同步是整份替換，非本選區與已完成項目會刪除；手動維護的額外白名單不會保留。
- 自適應只辨識目前已載入 Litematica 的 placement，不會搜尋磁碟上的藍圖檔案；判定最近不是判定 Printer 此刻實際正在列印哪個 placement。
- `autoSyncWhitelist=true` 會隨掃描自動改寫 `whitelist`，空白時強制禁止全部；原有 `whitelistEmptyAllowAll` 不會放寬自適應模式。
- 庫存門檻以 item 最大堆疊數計算；若 `maxItemsPerOrder` 太小，可能一次補不到 4 組，會等庫存再降到 <=1 組才再買。
- 庫存門檻是購買時機的滯迴，不保證庫存永久維持在 4 組；實際仍受投影缺額、冷卻與單次上限限制。
- Litematica 材料清單的計算速度由 Litematica 控制；本模組不會自動重建，避免在計算中打斷。若選取範圍很大，計算可能需要數分鐘；可縮小 placement／選區，或使用 Litematica 的渲染層限制（會影響統計範圍）。
- 進度以 chunk 計算，ETA 為依目前速度的估算；如果剩餘區塊因為距離／載入範圍而無法處理，進度會停滯並顯示「等待區塊載入」。
- 本模組不查詢伺服器餘額、價格或稅金；伺服器商店規則由伺服器決定。
- 材料清單依賴客戶端世界；遠離 placement、區塊未載入或 Litematica 尚未更新時，`countMissing` 可能不準確。
- 伺服器錯誤訊息若沒有包含物品 ID，本模組會以「最近送出的待送達物品」為封鎖目標；可能不是真正失敗的物品。
- 本專案沒有自動化測試；建置驗證為實際編譯，功能驗證請使用第 10 節手動測試清單。

---

## 9. 建置

需求：

- JDK 25 以上（本專案以 `--release 25` 編譯；Gradle 9.8.2 本身可在 JDK 27 執行）
- 網路可連線 Fabric Maven、Modrinth Maven 與 Gradle distribution

```bash
./gradlew build
```

Windows：

```bat
gradlew.bat build
```

輸出：

```
build/libs/litematica-autobuy-1.0.0.jar
```

- Litematica 與 MaLiLib 由 Modrinth Maven 取得。26.3 官方範例與 Printer 建置腳本都使用 `implementation` 取得 mod jar（Loom 1.19 會處理 remap），本專案沿用；沒有使用舊式 `modImplementation`。
- Printer 不需要在編譯期存在；本模組以反射讀取 `ConfigUtils`／`ModuleManager`。
- 若要在開發環境一起載入 Printer，把 `litematica-printer-1.1.3-hotfix.1+26.3.jar` 放進 `libs/`，Gradle 會以 `modLocalRuntime` 載入；詳見 `libs/README.md`。
- 若依賴解析失敗，先確認 `gradle.properties` 的版本與 Maven 連線；不要混用 Yarn 與官方 mappings。

---

## 10. 手動測試清單

- [ ] `./gradlew build` 成功，jar 產生。
- [ ] 26.3 + Fabric + 所有依賴可啟動，無崩潰。
- [ ] 控制設定出現 Litematica AutoBuy 分類，四個快捷鍵預設未綁定。
- [ ] 未選取 placement：按掃描會提示且不送指令。
- [ ] 材料清單尚未完成：提示並跳過，不重建投影。
- [ ] 材料清單持續為空：每次空清單只提示一次；action bar 顯示百分比／剩餘區塊／ETA；停滯時提示載入區塊；完成後提示「材料清單已就緒」。
- [ ] 空白白名單：不送 `/buy`，聊天顯示白名單阻擋項目。
- [ ] 白名單加入 `stone` 或 `minecraft:stone`：正確正規化，不重複。
- [ ] 選取有缺料的投影：只送白名單內且缺額 > 0 的物品。
- [ ] 指令格式為 `buy <item_id> <count>`；item ID 不帶 `minecraft:`，不含前導 `/`。
- [ ] 兩條指令間隔至少 1000 ms。
- [ ] 每輪最多排入 4 條；單次不超過 576。
- [ ] 缺額大於 576 時，後續掃描只補剩餘差額，不超買。
- [ ] 待送達期間同一物品不重複下單；庫存增加後重新計算。
- [ ] 模擬延遲到帳：30 秒後重試，最多 2 次，之後封鎖並提示。
- [ ] 模擬伺服器失敗訊息：封鎖該物品；重新載入設定可解除。
- [ ] 模擬權限錯誤：暫停送出 60 秒並提示。
- [ ] Printer Work Switch／Print 任一關閉：預設 gate 不掃描；兩者開啟才掃描。
- [ ] `PRINTER_PRINTING` 模式：只有 `PrinterStatus.PRINTING` 才掃描。
- [ ] `ALWAYS` 模式：不需要 Printer 也能掃描。
- [ ] 創造模式、GUI 開啟、主背包全滿：安全跳過並提示。
- [ ] 多個 placement：關閉自適應只計算手動選取者；開啟自適應只計算最近已啟用者。
- [ ] 同 ID 不同 components：合併成一種缺料。
- [ ] 綁定「同步白名單」：選取 placement 後按同步，`missing > 0` 加入、`missing == 0` 從白名單移除。
- [ ] 同步完整替換白名單：非目標選區與尚需放置數為 0 的舊項目全部刪除。
- [ ] 自適應模式選擇離玩家已啟用區域最近的 placement，不受藍圖原點距離誤導；多子區域間隙與重疊等距時判定穩定。
- [ ] 自動購買關閉／Printer 未安裝時，自適應名單仍更新，不會下單；啟用自動購買後仍須通過 Printer gate。
- [ ] 最近目標 A 換成 B 時，先清除 A 未送出指令；B 計算完成前不使用 A 的白名單下單，待送達紀錄保留。
- [ ] 無目標／計算中／讀寫失敗：保留原名單並不購買；完成的空清單清空，且不受空名單允許全部設定影響。
- [ ] 同步後 config 的 `whitelist` 更新，其他設定欄位保留；未選 placement／材料清單未完成時不修改白名單。
- [ ] 手動同步不送出 `/buy`，且自動掃描延後一輪；白名單有變更時未送出的舊佇列被清除。
- [ ] `autoSyncWhitelist=true`：掃描前自動同步；有變更時該輪不購買，下一輪才使用新名單。
- [ ] 庫存 >=4 組：不買；庫存 1–4 組：不買；庫存 <=1 組：觸發購買。
- [ ] 觸發時買到 4 組，但不超過投影缺額；方塊以 64 為一組，16/1 堆疊物品按自身 max stack。
- [ ] 購買後庫存達 4 組不再重複下單；Printer 消耗後降到 <=1 組才再補。
- [ ] `stockGateEnabled=false` 可恢復原本缺額購買行為。
- [ ] 模式快捷鍵：精確層 ↔ 大量；Litematica 材料清單類型切到渲染層／全部並重新計數。
- [ ] 材料清單計算中按模式鍵：拒絕切換並提示等進度／ETA 完成。
- [ ] 精確層 + 材料清單是 All：自動切換為渲染層並重算；不會買整份投影。
- [ ] 精確單層 + 下方所有／上方所有／範圍／全部：不購買、不同步白名單，提示切換至單層。
- [ ] 單層未放置 100、錯放 20、庫存 30：需求清單為 100，購買 70；正確放置與錯放都不加入需求。
- [ ] 精確單層購買量＝`max(0, 本層 countMissing − countMismatched − 庫存)`，忽略 4／1 門檻與 TOTAL 設定。
- [ ] 換層／軸向／placement 後，舊計數結果及未送出佇列不可下單；計算中多次掃描不重啟任務。
- [ ] 完成的空層顯示無缺料；放置新方塊後下一批次重新計數，不沿用舊清單。
- [ ] 大量模式：整份投影（全部）＋4/1 門檻；正確放置不計。
- [ ] 白名單同步使用目前購買模式的材料清單。
- [ ] 背包滿／空格不足：本輪不送出，顯示需要格數與目前空格；清空／消耗物品後自動恢復。
- [ ] 背包全滿但同物品部分堆疊有空間：容量計算會允許放入，不誤判全滿。
- [ ] 本輪批次＋待送達需要的格數 >36：提示降低單次／每輪上限，不會無限等待。
- [ ] `skipWhenInventoryFull=false`：不等待容量，直接送單（測試用，可能掉地）。
- [ ] 重新載入設定：新白名單、冷卻、上限生效；待送達不清除。
- [ ] 離開／切換連線：自動補料關閉，狀態清除。
- [ ] `debugLog=true` 時 logs 有掃描／待送達／失敗細節。

---

## 11. 建置驗證狀態

以下是 2026-11-12 在本機實際執行的結果：

- 原始碼與版本 API：精確單層與放置列舉 API 已依快取的 Litematica／MaLiLib 26.3、26.2 jar 簽章與計數位元碼核對。
- Gradle wrapper：Gradle 9.8.2 可執行；本機以 JDK 25 執行，編譯目標為 Java 25。
- `./gradlew build`：**BUILD SUCCESSFUL in 42s**（Fabric Loom 1.19.4；含自適應白名單與 Printer 佇列閘門）。
- 依賴解析：Fabric API 0.165.2+26.3、Litematica 0.29.1、MaLiLib 0.30.2、Cloth Config 26.3.159。
- 產物：`build/libs/litematica-autobuy-1.0.0.jar`（約 85 KB，SHA-256 `43e2c45cd66a516a70141cf71b7bfc740bf78da51f15c0f43a56b38a54144239`）。
- 實際原始碼搭配外部 API 替身：134 項隔離回歸測試在 JDK 25／27 均通過（各 1,146 個斷言），涵蓋最近藍圖判定、白名單完整替換、單層限制、錯放排除、庫存差額、非同步完成／取消、換層與 placement 變動、手動操作接續、Printer 佇列閘門及佇列容量保護；不等同真實世界／GUI 測試。
- 已在獨立開發客戶端（26.3）看到 AutoBuy 的六個快捷鍵分類與可綁定按鍵；GUI 內容、實際材料計數與 `/buy` 尚未於連線世界驗證。
- 已檢查 jar：`fabric.mod.json`、client entrypoint、`zh_cn`／`zh_tw`／`en_us` 語系（含 `sync_whitelist`、`purchase_mode`、`config_gui` 快捷鍵）都存在；沒有 Mixin，也沒有打包 Litematica／MaLiLib／Printer。
- 白名單同步、庫存門檻、精確單層／大量模式切換、背包容量等待、空材料清單進度 ETA 與 Cloth Config 設定 GUI 已加入並通過編譯；實際選取 placement、GUI 儲存、切換、容量等待與購買仍需依第 10 節手動測試。
- **尚未**實際啟動 Minecraft client、載入世界、選取 placement、連線伺服器或驗證 `/buy` 的回應；這些屬於待你在 26.3 環境完成的功能測試。
- Printer 未放入 `libs/`，因此建置時沒有做 Printer 的 dev runtime 載入測試；正式遊玩請直接把 Printer jar 放進 `mods/`。

---

## 12. 第三方與授權

- 本專案非 Litematica、Litematica Printer 或 MaLiLib 的官方專案。
- 不包含、不修改、不重新散布上述模組的程式碼或資產；執行時以公開 API／反射整合。
- Litematica Printer water2004 分支為 AGPL-3.0；使用與散布其 jar 時請遵守其授權。
- 本專案採用 **MIT License**，完整條文見根目錄 `LICENSE`。
- 使用前請確認伺服器規則允許自動購買指令；退回、誤買、伺服器封鎖等風險由使用者自行承擔。
