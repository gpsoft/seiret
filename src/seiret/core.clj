(ns seiret.core
  (:require
   [clojure.tools.cli :as cli]
   [clojure.string :as str]
   [seiret.win32 :as win32]
   [seiret.util :as u])
  (:import
   [com.sun.jna.platform.win32 User32 WinDef$HWND])
  (:gen-class))

(def ^:private ^:dynamic *edge-factor* 7)

(def ^:private cli-options
  [["-l" "--list" "Show list of windows"]
   ["-h" "--help" "Show usage"]])

(defn- show-usage!
  [options-summary]
  (println "Usage: seiret [OPTIONS] LAYOUT")
  (println "Ex:    seiret layout.edn")
  (println)
  (println "Options:")
  (println options-summary)
  )

(defn- show-list!
  []
  (win32/enum-windows
   (fn [wnd]
     (let [caption (win32/window-caption wnd)
           klass (win32/window-class wnd)]
      (prn (str klass ": " caption)))
     true)))

(defn- bias-left
  [v]
  (- v *edge-factor*))

(defn- bias-width
  [v]
  (+ v (* *edge-factor* 2)))

(defn- bias-height
  [v]
  (+ v *edge-factor* -2))

(defn- bias-rect
  [{:keys [pos size]}]
  (let [[l t] pos
        [w h] size]
    {:pos [(bias-left l) t]
     :size [(bias-width w) (bias-height h)]}))

(defn- gap-rect
  [gap factor-h factor-v {:keys [pos size]}]
  (let [[l t] pos
        [w h] size]
    {:pos [(+ l gap) (+ t gap)]
     :size [(- w gap (if (= factor-h 2) 0 gap))
            (- h gap (if (= factor-v 2) 0 gap))]}))

(defn- preset->params
  [preset]
  (case preset
    :full [[:A :Z]]
    :side-by-side [[:A :B] [:Y :Z]]
    :big-4 [[:A] [:B] [:Y] [:Z]]
    :eyes [[:G] [:T]]
    :main-sub [[:C :K] [:V :X]]
    :sub-main [[:C :E] [:I :X]]
    :top-down [[:A :Y] [:B :Z]]
    nil))

(defn- div-factor-h
  [anchor]
  (if (anchor #{:A :B :F :G :H :S :T :U :Y :Z}) 2 3))

(defn- div-factor-v
  [anchor]
  (if (anchor #{:A :B :Y :Z}) 2 3))

(defn- pos-factor-h
  [anchor]
  (if (anchor #{:A :B :C :D :E :F :G :H}) 0
    (if (anchor #{:V :W :X}) 2 1)))

(defn- pos-factor-v
  [anchor]
  (if (anchor #{:A :Y :C :I :V :F :S}) 0
    (if (anchor #{:E :K :X :H :U}) 2 1)))

(defn- gap-factor-h
  [anchor]
  (if (anchor #{:Y :Z :V :W :X :S :T :U}) 1 2))

(defn- gap-factor-v
  [anchor]
  (if (anchor #{:B :Z :E :K :X :H :U}) 1 2))

(defn- part
  [v r]
  (int (* r (/ v 100))))

(defn- size-h
  [W div-h pos-h]
  (if (= div-h 2)
    (quot W 2)
    (if (= pos-h 1) (part W 14) (part W 43))))

(defn- size-v
  [H div-v pos-v]
  (if (= div-v 2)
    (quot H 2)
    (if (= pos-v 1) (part H 70) (part H 15))))

(defn- grid-h
  [W div-h]
  (if (= div-h 2)
    [0 (size-h W div-h 0)]
    [0 (size-h W div-h 0) (+ (size-h W div-h 0) (size-h W div-h 1))]))

(defn- grid-v
  [H div-v]
  (if (= div-v 2)
    [0 (size-v H div-v 0)]
    [0 (size-v H div-v 0) (+ (size-v H div-v 0) (size-v H div-v 1))]))

(defn- anchor->rect
  [a]
  (let [[W H] (win32/workarea-size)
        div-h (div-factor-h a)
        div-v (div-factor-v a)
        pos-h (pos-factor-h a)
        pos-v (pos-factor-v a)
        w (size-h W div-h pos-h)
        h (size-v H div-v pos-v)
        ]
    {:pos [(nth (grid-h W div-h) pos-h) (nth (grid-v H div-v) pos-v)]
     :size [w h]}))

(defn- anchors->rect
  [gap [lt rb]]
  (let [rb (if rb rb lt)
        rect-lt (anchor->rect lt)
        rect-rb (anchor->rect rb)
        gap-h (gap-factor-h rb)
        gap-v (gap-factor-v rb)]
    (let [{[l t] :pos} rect-lt
          {:keys [pos size]} (anchor->rect rb)
          r (+ (first pos) (first size))
          b (+ (second pos) (second size))]
      (gap-rect gap gap-h gap-v
                {:pos [l t]
                 :size [(- r l) (- b t)]}))))

(defn- metrics-1
  [gap param]
  (if (map? param)
    param
    (bias-rect (anchors->rect gap param))))

(defn- metrics
  [{:keys [preset gap edge-factor params] :or {gap 0 edge-factor *edge-factor* params []}}]
  (let [params (if preset (preset->params preset) params)]
    (binding [*edge-factor* edge-factor]
      (map #(metrics-1 gap %) params))))

(defn- seiretsu-1
  [wnd rect]
  (when (and wnd rect)
    (let [{:keys [pos size]} rect]
      (win32/place-window! wnd pos size))))

(defn- match?
  [wnd wclass title]
  (let [wclass? (or (nil? wclass)
                    (= (win32/window-class wnd) (name wclass)))
        title? (or (nil? title)
                   (= (win32/window-caption wnd) title))]
    (and wclass? title?)))

(defn- find-window-nth
  [wclass title ix]
  (let [match-wnd (atom nil)
        match-ix (atom 0)]
    (win32/enum-windows
     (fn [wnd]
       (if (match? wnd wclass title)
         (if (= ix @match-ix)
           (do (reset! match-wnd wnd)
               false)
           (do (swap! match-ix inc)
               true))
         true)))
    @match-wnd))

(defn- capture-1
  [{:keys [wclass title ix]} wnds]
  (if (nil? ix)
    (win32/find-window wclass title)
    (find-window-nth wclass title ix)))

(defn- capture
  [specs]
  (let [wnds (atom [])]
    (map #(capture-1 % wnds) specs)))

(defn- seiretsu
  [lay]
  (let [capture-specs (:capture lay)
        wnds (capture capture-specs)
        rects (metrics (:seiretsu lay))]
    (dorun (map seiretsu-1 wnds rects))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        {:keys [list help]} options
        err-options? (when errors
                       (println (str/join \newline errors))
                       true)]
    (cond
     (or help
         err-options?
         (and (not list)
              (empty? arguments))) (show-usage! summary)
     list (show-list!)
     :else (seiretsu (u/read-edn! (first arguments) {})))))

(comment

 (-main "--help")
 (-main "--list")
 (-main "layout.edn")
 (cli/parse-opts ["--list"] cli-options)
 (cli/parse-opts ["layout.edn"] cli-options)

 (capture [{:wclass :Vim}
           {:wclass :MozillaWindowClass}
           {:wclass :CabinetWClass :ix 0}
           {:wclass :CabinetWClass :ix 1}
           ])

 (let [wnd (win32/find-window :CabinetWClass)]
   (seiretsu-1 wnd (metrics-1 10 [:B])))

 (seiretsu
  {:capture [{:wclass :Vim}
             {:wclass :MozillaWindowClass}
             {:wclass :CabinetWClass}
             ]
   :seiretsu {
              :gap 10
              :edge-factor 7
              :preset :sub-main
              }
   })

 )

