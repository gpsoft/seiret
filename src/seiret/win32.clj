(ns seiret.win32
  (:require
   [clojure.string :as str]
   [seiret.util :as u])
  (:import
   [com.sun.jna Native Pointer]
   [com.sun.jna.platform.win32
    User32 WinDef$HWND WinDef$PVOID WinDef$RECT WinDef$DWORD
    Shell32 ShellAPI ShellAPI$APPBARDATA
    WinUser$WNDENUMPROC]
   [com.sun.jna.ptr IntByReference]))

 (gen-interface
  :name seiret.win32.IUser32
  :extends [com.sun.jna.platform.win32.User32]
  :methods [[SystemParametersInfoA
             [java.lang.Integer java.lang.Integer
              com.sun.jna.platform.win32.WinDef$PVOID
              java.lang.Integer]
             java.lang.Boolean]
            ])

(def ^:private ^:dynamic *user32* (Native/loadLibrary "User32" seiret.win32.IUser32))
(def ^:private ^:dynamic *edge-factor* 7)

(defn desktop-size
  []
  (let [dw (.GetSystemMetrics User32/INSTANCE User32/SM_CXSCREEN)
        dh (.GetSystemMetrics User32/INSTANCE User32/SM_CYSCREEN)]
    [dw dh]))

(defn workarea-size
  []
  (let [rect (WinDef$RECT.)
        SPI_GETWORKAREA 0x30]
    (.SystemParametersInfoA *user32*
                            (Integer/valueOf SPI_GETWORKAREA)
                            (Integer/valueOf 0)
                            (WinDef$PVOID. (.getPointer rect))
                            (Integer/valueOf 0))
    (.read rect)
  [(.right rect) (.bottom rect)]))

(defn- RECT->rect
  [RECT]
  (let [left (.left RECT)
        top (.top RECT)]
    [[left top] [(- (.right RECT) left) (- (.bottom RECT) top)]]))

(defn taskbar-rect
  []
  (let [data (ShellAPI$APPBARDATA.)
        _ (.SHAppBarMessage Shell32/INSTANCE (WinDef$DWORD. ShellAPI/ABM_GETTASKBARPOS) data)
        RECT (.rc data)]
    (RECT->rect RECT)))

(defn find-window
  ([a]
   (if (keyword? a)
     (find-window a nil)
     (find-window nil a)))
  ([wclass title]
   (when (or wclass title)
     (.FindWindow User32/INSTANCE (when wclass (name wclass)) title))))

(defn window-visible?
  [wnd]
  (.IsWindowVisible User32/INSTANCE wnd))

(defn- window-style?
  [wnd flag]
  (let [style (.GetWindowLong User32/INSTANCE wnd User32/GWL_STYLE)]
    (= (bit-and style flag) flag)))

(defn window-minimized?
  [wnd]
  (window-style? wnd User32/WS_MINIMIZE))

(defn window-maximized?
  [wnd]
  (window-style? wnd User32/WS_MAXIMIZE))

(defn window-popup?
  [wnd]
  (window-style? wnd User32/WS_POPUP))

(defn window-rect
  [wnd]
  (let [RECT (WinDef$RECT.)]
    (.GetWindowRect User32/INSTANCE wnd RECT)
    (RECT->rect RECT)))

(defn window-caption
  [wnd]
  (let [caption-len (.GetWindowTextLength User32/INSTANCE wnd)
        buf-len (inc caption-len)
        buf (char-array buf-len)
        _ (.GetWindowText User32/INSTANCE wnd buf buf-len)]
    (apply str buf)))

(defn window-class
  [wnd]
  (let [buf-len 1024
        buf (char-array buf-len)
        _ (.GetClassName User32/INSTANCE wnd buf buf-len)]
    (apply str (take-while #(not= (int %) 0) buf))))

(defn place-window!
  [wnd pos size]
  (let [flags User32/SWP_NOZORDER
        flags (if pos flags User32/SWP_NOMOVE)
        flags (if size flags User32/SWP_NOSIZE)
        [x y] (if pos pos [0 0])
        [w h] (if size size [0 0])]
    (.SetWindowPos User32/INSTANCE
                   wnd (WinDef$HWND.)
                   x y w h flags)))

(defn enum-windows
  [fn-wnd]
  (let [cb (reify WinUser$WNDENUMPROC
             (callback [this wnd pdata]
               (let [visible? (window-visible? wnd)
                     caption (window-caption wnd)
                     popup? (window-popup? wnd)]
                 (if (and visible? (not (empty? caption)) (not popup?))
                   (fn-wnd wnd)
                   true))))
        pdata Pointer/NULL]
    (.EnumWindows User32/INSTANCE cb pdata)))

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
  [{:keys [preset gap edge-factor params] :or {gap 0 edge-factor *edge-factor* params []}}]
  (if preset
    (binding [*edge-factor* edge-factor]
      (side-by-side gap))
    params))


(comment

 (desktop-size)
 (workarea-size)
 (taskbar-rect)

 (find-window :Vim)
 (find-window nil "VIM")
 (find-window nil "Emacs")
 (find-window nil nil)

 (let [wnd (find-window :Vim)]
   (window-rect wnd))

 (let [wnd (find-window :Vim)]
   #_(place-window! wnd nil [100 200])
   #_(place-window! wnd [100 200] nil)
   (place-window! wnd [10 10] [950 1030]))

 (let [wnd (find-window :Vim)]
   (window-visible? wnd))

 (let [wnd (find-window :Vim)]
   (window-caption wnd))

 (let [wnd (find-window :Vim)]
   (window-class wnd))

 (enum-windows
  (fn [wnd]
    (let [maximized? (window-maximized? wnd)
          minimized? (window-minimized? wnd)
          caption (window-caption wnd)
          klass (window-class wnd)]
      (when maximized? (.ShowWindow User32/INSTANCE wnd User32/SW_SHOWNORMAL))
      (when minimized? (.ShowWindow User32/INSTANCE wnd User32/SW_SHOWNORMAL))
      (u/tap! (str caption "-" klass)))
    true))



 )
