(ns seiret.core
  (:require
   [clojure.tools.cli :as cli]
   [clojure.string :as str]
   [seiret.util :as u])
  (:import
   [com.sun.jna.platform.win32 User32 WinDef$HWND])
  (:gen-class))

(def ^:private ^:dynamic *edge-factor* 7)

(def ^:private cli-options
  [["-h" "--help" "Show usage"]])

(defn- show-usage!
  [options-summary]
  (println "Usage: seiret [OPTIONS] LAYOUT")
  (println "Ex:    seiret layout.edn")
  (println)
  (println "Options:")
  (println options-summary)
  )

(defn- capture-1
  [spec]
  (let [{:keys [wclass title]} spec
        wnd (.FindWindow User32/INSTANCE (name wclass) title)]
    wnd))

(defn- seiretsu-1
  [param wnd]
  (when wnd
    (let [{:keys [pos size]} param
          flags (if size
                  User32/SWP_NOZORDER
                  (bit-or User32/SWP_NOSIZE User32/SWP_NOZORDER))
          [x y] pos
          [w h] (if size size [0 0])]
      (.SetWindowPos User32/INSTANCE
                     wnd (WinDef$HWND.)
                     x y w h flags))))

(defn- bias-left
  [v]
  (- v *edge-factor*))

(defn- bias-width
  [v]
  (+ v (* *edge-factor* 2)))

(defn- side-by-side
  [gap]
  (let [dw (.GetSystemMetrics User32/INSTANCE User32/SM_CXFULLSCREEN)
        dh (.GetSystemMetrics User32/INSTANCE User32/SM_CYFULLSCREEN)
        w (quot (- dw (* gap 3)) 2)
        h (- dh gap gap)]
    [{:pos [(bias-left gap) gap] :size [(bias-width w) h]}
     {:pos [(bias-left (+ gap w gap)) gap] :size [(bias-width w) h]}]))

(defn- metrics
  [{:keys [preset gap edge-factor params]}]
  (if preset
    (binding [*edge-factor* edge-factor]
      (side-by-side gap))
    params))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        {:keys [action help]} options
        err-options? (when errors
                       (println (str/join \newline errors))
                       true)]
    (cond
     (or help err-options? (empty? arguments)) (show-usage! summary)
     :else (let [lay (u/read-edn! (first arguments) {})
                 capture-specs (:capture lay)
                 params (metrics (:seiretsu lay)) ]
             (dorun (->> capture-specs
                         (map capture-1)
                         (map seiretsu-1 params)))))))

(comment

 (-main "--help")
 (cli/parse-opts [] cli-options)

 (let [lay (u/read-edn! "layout.edn" {})
       capture-specs (:capture lay)
       params (metrics (:seiretsu lay)) ]
   (->> capture-specs
        (map capture-1)
        (map seiretsu-1 params)))

 (import '[com.sun.jna.platform.win32 User32 WinDef$HWND])

 (let [wnd 
       #_(.FindWindow User32/INSTANCE "CabinetWClass" "maru")
       (.FindWindow User32/INSTANCE "CASCADIA_HOSTING_WINDOW_CLASS" nil)
       dw (.GetSystemMetrics User32/INSTANCE User32/SM_CXFULLSCREEN)
       dh (.GetSystemMetrics User32/INSTANCE User32/SM_CYFULLSCREEN)]
   (.MoveWindow User32/INSTANCE wnd -7 0 1934 1000 true)
   #_(.SetWindowPos User32/INSTANCE wnd (WinDef$HWND.) -7 0 0 0 (bit-or User32/SWP_NOSIZE User32/SWP_NOZORDER))
   dh
   )

 )

