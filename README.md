# Seiret(整列)

## 機能

- ウィンドウのレイアウトを調整する
- Windows専用

## 使い方

```shell: ウィンドウクラス名を調べる
PS D:\seiret> script\wndclass.ps1
PS D:\seiret> script\wndclass.ps1 12345
```

```edn: layout.edn
{:capture [{:class :MozillaWindowClass}
           {:class :Vim}
           ]
 :seiretsu {:gap 10
            :preset :side-by-side
            }
 }
```

```
$ java -jar seiret.jar layout.edn
```

## 開発

```
$ clj -M:dev
$ vim src/seiret/core.clj
  :Connect 5876 src

$ clj -M -m seiret.core
```

## リリース

```
$ clj -T:build clean
$ clj -T:build uber
```
