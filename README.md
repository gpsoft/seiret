# Seiret(整列)

## 機能

- ウィンドウのレイアウトを調整する
- Windows専用

## 使い方

##### ウィンドウクラス名を調べる
```powershell
PS D:\seiret> script\wndclass.ps1
PS D:\seiret> script\wndclass.ps1 12345
```

##### レイアウトファイル(`layout.edn`)を作る
```edn
{:capture [{:wclass :Vim}
           {:wclass :MozillaWindowClass}
           ]
 :seiretsu {;; use preset
            :preset :side-by-side
            :gap 10
            :edge-factor 7

            ;; or use custom params
            ;;:params [{:pos [0 7] :size [880 1020]}
            ;;         {:pos [873 7] :size [1042 1028]}]
            }
 }
```

##### 実行する
```powershell
PS D:\seiret> java -jar seiret.jar layout.edn
```

## 開発

```
PS D:\seiret> clj -M:dev
PS D:\seiret> vim src/seiret/core.clj
  :Connect 5876 src

PS D:\seiret> clj -M -m seiret.core
```

## リリース

```
PS D:\seiret> clj -T:build clean
PS D:\seiret> clj -T:build uber
```
