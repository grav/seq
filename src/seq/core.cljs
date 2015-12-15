(ns ^:figwheel-always seq.core
  (:require [reagent.core :as r]
            [goog.object :as g]
            [seq.util :as u]
            [seq.midi :as m]
            [seq.ui :as ui]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defonce app-state (r/atom {:bpm       120
                            :sustain 0.12
                            :sequences {}}))

(def latency 0.1)

(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(def all-notes-off [176 123 00])

(defn ding
  [out-id v start t]
  (when-let [out (-> (get-in @app-state [:midi :outputs])
                     (m/get-output out-id))]
    (let [{:keys [channel transpose]
           :or   {channel 0 transpose 0}} (get-in @app-state [:sequences out-id])
          t1 (* 1000 start)
          t2 (* 1000 (+ start t))]
      (.send out #js [(+ channel 144), (+ v transpose), 0x30] t1)
      (.send out #js [(+ channel 128), (+ v transpose), 0x00] t2))))


(defn play-sequence! [beat time]
  #_(swap! app-state assoc :pointer (mod beat 16))
  (let [p (mod beat 16)
        spt (secs-per-tick (:bpm @app-state))
        now (/ (.now (.-performance js/window)) 1000)
        new-notes (for [[k {:keys [sequence]}] (->> (get-in @app-state [:midi :outputs])
                                                    (map #(.-id %))
                                                    (select-keys (get-in @app-state [:sequences])))
                        :when sequence]
                    [k (->> sequence
                            (repeat)
                            (apply concat)
                            (map vector (range))
                            (drop p)
                            (map (fn [[i v]] [(- i p) v]))
                            (map (fn [[i v]] [(* spt i) v]))
                            (map (fn [[i v]] [(+ time i) v]))
                            (take-while (fn [[i _]] (< i (+ now (* 1.5 latency))))))])]
    (swap! app-state assoc :position p)
    (doseq [[k s] new-notes]
      (doseq [[i vs] s]
        (doseq [v vs]
          (ding k (+ 0x24 v) i (:sustain @app-state)))))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (or (->> (map count (map second new-notes))
                                             (apply max))
                                        0))
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(seq.core/play-sequence! beat' time') (* latency 1000)))))

(defn setup-midi! []
  (let [save-devices! (fn [ma]
                        (swap! app-state update-in [:midi] merge {:inputs  (m/inputs ma)
                                                                  :outputs (m/outputs ma)}))]
    (-> (js/navigator.requestMIDIAccess)
        (.then (fn [ma]
                 (save-devices! ma)
                 ;; Update devices continously
                 (set! (.-onstatechange ma) #(save-devices! ma)))))))

(defn handle-midi-select [state-key selection-key]
  (fn [val]
    (let [selected (->> (get-in @app-state [:midi state-key])
                        (filter #(= (.-id %) val))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn handle-val-change [output key val]
  (swap! app-state assoc-in [:sequences output key] val))

(defn step-clicked [output step-number key selected?]
  (let [seq (or (get-in @app-state [:sequences output :sequence])
                (vec (repeat 16 [])))
        keys (->> (get seq step-number)
                  (cons key)
                  (remove #(when selected? (= % key))))
        new-seq (assoc seq step-number keys)]
    (swap! app-state assoc-in [:sequences output :sequence] new-seq)))

(defn nudge [id v]
  (when-let [seq (get-in @app-state [:sequences id :sequence])]
    (swap! app-state assoc-in [:sequences id :sequence] (->> seq
                                                             (repeat)
                                                             (apply concat)
                                                             (drop (- 16 v))
                                                             (take 16)
                                                             vec))))

(defn save! [name]
  (->>
    (with-out-str (prn (:sequences @app-state)))
    (aset js/localStorage name)))

(defn restore! [name]
  (->> (aget js/localStorage name)
       (cljs.reader/read-string)
       (swap! app-state assoc :sequences)))

(r/render [:div
           [ui/root-view app-state {:did-mount         setup-midi!
                                    :step-clicked      step-clicked
                                    :handle-select     handle-midi-select
                                    :handle-val-change handle-val-change
                                    :nudge             nudge}]
           [ui/decay-view app-state #(swap! app-state assoc :sustain %)]
           [ui/session-view {:handle-select restore!
                             :handle-save   save!}]] (js/document.getElementById "app"))

(defonce go
         (play-sequence! 0 0))
