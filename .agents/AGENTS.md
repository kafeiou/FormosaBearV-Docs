# 專案鐵律 (Rules)
- **目錄規範 (Directory Conventions)**：專案內所有 Agent 相關設定與規範，一律統整於 `.agents/`（複數），嚴禁建立單數的 `.agent/` 目錄。
- **執行前確認 (Execution Confirmation)**：在修改檔案、執行指令或進行破壞性變更之前，永遠必須先向使用者提出計畫與變更內容，並取得明確同意後才可執行。
- **非同步任務同步 (Async Task Synchronization)**：當呼叫非同步的背景子代理或任務時，永遠必須等待回傳完成訊息後，才可進行 Git Commit 或分支操作。
- **防呆與錯誤處理 (Error Handling & Guardrails)**：在實作任何核心邏輯或 UI 互動時，必須主動考慮極端情況並加入適當的阻擋機制。
- **流程圖文件化 (Flowchart Documentation)**：產生的系統架構或邏輯流程圖，必須使用 `mermaid` 語法記錄到 Spec 文件中。
- **自動同步主文件 (Auto-Sync Master Docs)**：變更歸檔後，必須自動重新生成 `openspec/specs/README.md`。
