(ns seq.launchpad
  (:require [seq.scale]
            [seq.util :as util]))

(defn is-launchpad? [d]
  "check if device is a launchpad"

  (-> {:lp-mini {:name "Launchpad Mini"
                 :manu "Focusrite A.E. Ltd"}
       :lp      {:name "Launchpad"
                 :manu "Novation DMS Ltd"}}
      (vals)
      (set)
      (contains? {:name (.-name d)
                  :manu (.-manufacturer d)})))

(defn modifier [[a b vel]]
  (when-let [modifier (-> {[176 108] :modifier/session
                           [176 109] :modifier/user1
                           [176 110] :modifier/user2
                           [176 111] :modifier/mixer}
                          (get [a b]))]
    {:name     modifier
     :enabled? (= vel 127)}))

(defn- inverse [n]
  (->> (* 8 (inc (int (/ n 8))))
       (- 64)
       (+ (mod n 8))))

(defn midi->pad [note]
  (-> (+ (mod note 16)
         (* 8 (int (/ note 16))))
      (inverse)))

(defn pad->midi [n]
  (let [m (inverse n)]
    (-> m
        (/ 8)
        (int)
        (* 8)
        (+ m))))

(def clear-all [176 0 0])

(defn navigation [midi-msg modifier]
  (get {[176, 104, 127] [:y #(+ % (if modifier 1 8))]       ;; up
        [176, 105, 127] [:y #(- % (if modifier 1 8))]       ;; down
        [176, 106, 127] [:x (constantly 0)]                 ;; left
        [176, 107, 127] [:x (constantly 8)]                 ;; right
        } midi-msg))

(defn right-side-arrow [[a b c]]
  (when (and (= a 144)
             (= c 127)
             (zero? (mod (- b 8) 16)))
    (/ (- b 8) 16)))

(defn crop-data
  ([data]
   (crop-data data 8 8))
  ([data width height]
   (->> data
        (take height)
        (map #(take width %)))))

(defn offset-data [data x y]
  (->> data
       (drop y)
       (map #(drop x %))))

(defn render [state lp data]
  (let [now (/ (.now (.-performance js/window)) 1000)
        diff (->> data
                  (map (fn [a b] (when (not= a b) b)) (or @state (repeat false))))]
    (when (nil? @state)
      (.send lp (clj->js clear-all) now))
    (reset! state data)

    (doseq [[i v] (map vector (range) diff)]
      (when (true? v)
        (.send lp #js [144, (pad->midi i), 0x30] now))
      (when (false? v)
        (.send lp #js [144, (pad->midi i), 0x00] now))) 50))


(defn sequence->lp-data [sequence]
  (map #(map (fn [v] (contains? (set v) %)) sequence)
       (range) #_(seq.scale/inf-scale seq.scale/penta)))

(defn pad-note [[a b c]]
  (when (and (= a 144)
             (>= b 0)
             (<= b 119)
             (< (mod b 16) 8)
             (= c 127))
    b))

(defn- on-midi-message [tracks launchpad update-launchpad step-clicked e]
  ;; navigation
  (let [{:keys [modifiers]} launchpad
        midi-msg (->> e.data
                      (js/Array.from)
                      (js->clj))]
    (when-let [[k f] (navigation midi-msg (:modifier/user1 modifiers))]
      (let [old-val (or (get launchpad k) 0)
            new-val (max 0 (f old-val))]
        (update-launchpad (assoc launchpad k new-val))))

    ;; right-side arrows
    (when-let [index (right-side-arrow midi-msg)]
      (if (:modifier/user1 modifiers)
        (update-launchpad (assoc launchpad :index index))))

    ;; notes
    (when-let [note (pad-note midi-msg)]
      (prn note)
      (let [pad-note (midi->pad note)
            step-number (mod pad-note 8)
            k (int (/ pad-note 8))
            {:keys [x y index]
             :or   {x 0 y 0 index 0}} launchpad
            idx (min index (dec (count tracks)))
            {:keys [device]} (get (vec tracks) idx)]
        (step-clicked (.-id device) (+ x step-number) (+ y k))))

    (when-let [{:keys [name enabled?]} (modifier midi-msg)]
      (update-launchpad (merge-with merge launchpad {:modifiers {name enabled?}})))))

(defn on-render [tracks {:keys [x y index]
                         :or   {x     0
                                y     0
                                index 0}}
                 position
                 render-state lp]
  (let [idx (min index (dec (count tracks)))
        sequence (get-in (vec tracks) [idx :sequence :sequence])
        data (-> (or sequence (repeat '()))
                 (sequence->lp-data)
                 (offset-data x y)
                 (crop-data)
                 (flatten))]
    (render render-state
            lp
            (->> (map-indexed (fn [i v] (if (= i position) true v)) (repeat 64 false))
                 (map (fn [a b] (or a b)) data)))))

(defn init [app-state lp-in lp-out step-clicked]
  (set! lp-in.onmidimessage (fn [e]
                              (let [{:keys [midi launchpad sequences]} @app-state]
                                (seq.launchpad/on-midi-message (->> (:outputs midi)
                                                                    (remove is-launchpad?)
                                                                    (util/tracks sequences))
                                                               launchpad
                                                               #(swap! app-state assoc :launchpad %)
                                                               step-clicked e))))
  (let [render-state (atom)]
    (js/setInterval
      #(let [{:keys [sequences midi launchpad position]} @app-state]
        (seq.launchpad/on-render
          (->> (:outputs midi)
               (remove is-launchpad?)
               (util/tracks sequences))
          launchpad
          position
          render-state
          lp-out))
      100)))

