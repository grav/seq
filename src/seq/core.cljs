(ns ^:figwheel-always seq.core
  (:require [reagent.core :as r]
            [goog.object :as g]
            [seq.util :as u]
            [seq.midi :as m]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(defonce app-state (r/atom {:bpm      120
                            :sequence [[0] [] [] [0] [] [] [0] [] [] [] [0] [] [0] [] [] []]
                            :title    "Hello"}))

(def cell-size 20)

(defonce context (js/AudioContext.))

(defonce gain (.createGain context))

(.connect gain (.-destination context))

(set! (.. gain -gain -value) 0.3)

(defn sequence->steps [seq]
  (map vector (range) seq) )

(defn remove-empty-steps [seq]
  (filter (fn [[_ v]] (not (nil? v))) seq))

(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(defn inf-seq [f]
  (let [length 16
        f' f #_ #(filter (partial > length) (f))]
    (->> (map (fn [s it] (map (fn [[i v]] [(+ i (* length it)) v]) s)) (repeatedly f') (range))
       (apply concat))))

(defn beep
  ([f]
    (beep f 0.05))
  ([f t]
    (beep f (.-currentTime context) t))
  ([f start t]
   (let [osc (.createOscillator context)]
     (.connect osc gain)
     (set! (.. osc -frequency -value) f)
     (.start osc start)
     (.stop osc (+ start t)))))

(defn ding
  [v start t]
  (when-let [out (get-in @app-state [:midi :out])]
    (.send out #js [0x90, v, 0x30] (* 1000 start))
    (.send out #js [0x89, v, 0x30] (* 1000 (+ start t)))))

(defn play-sequence! [beat time sequence]
  (swap! app-state assoc :pointer (mod beat 16))
  (let [spt (secs-per-tick (:bpm @app-state))
        ;;now (.-currentTime context)
        now (/ (.now (.-performance js/window)) 1000)
        new-notes (->> sequence
                       (map (fn [[i v]] [(- i beat) v]))
                       (map (fn [[i v]] [(* spt i) v]))
                       (map (fn [[i v]] [(+ time i) v]))
                       (take-while (fn [[i v]] (< i (+ now 0.75)))))]



    (doseq [[i vs] new-notes]
      (doseq [v vs]
        (ding (+ 0x41 v) i 0.12)))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (count new-notes))
          _ (prn "diff" diff)
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(play-sequence! beat' time' (drop (count new-notes) sequence)) 500))))


(defn step [{:keys [selected? playing? step-number key]}]
  [:div {:on-click (fn [_]
                     (let [keys (->> (get-in @app-state [:sequence step-number])
                                     (cons key)
                                     (remove #(when selected? (= % key))))]
                       (swap! app-state assoc-in [:sequence step-number] keys)))
         :style    {:background-color (if playing? "yellow" "black")
                    :height           cell-size
                    :width            cell-size
                    :padding          2}}
   [:div {:style {:background-color (if selected? "red" "white")
                  :height           "100%"
                  :width            "100%"}}]])

(defn header [{:keys [title]}]
  [:div title])

(defn selector [keys vals current on-change]
  [:select {:value current :on-change #(on-change (.-value (.-target %)))}
   (map (fn [k v] [:option {:key   (or k "nil")
                            :value k} v]) (cons nil keys) (cons "---" vals))])

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
                        (filter #(= (hash %) (int val)))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn root [_]
  (r/create-class
    {:reagent-render      (fn [] (let [{:keys [title sequence pointer midi]} @app-state]
                                   [:div

                                    (when-let [{:keys [in out inputs outputs]} midi]
                                      [:div
                                       [:div "Input: " [selector
                                                        (map hash inputs)
                                                        (map #(.-name %) inputs)
                                                        (hash in)
                                                        (handle-midi-select :inputs :in)]]
                                       [:div "Output: " [selector
                                                         (map hash outputs)
                                                         (map #(.-name %) outputs)
                                                         (hash out)

                                                         (handle-midi-select :outputs :out)]]])
                                    [header {:title title}]
                                    [:div {:style {:display "flex"}}
                                     (map (fn [[i vs]]
                                            [:div
                                             (->> (range 16)
                                                  (map (fn [j] (contains? (set vs) j)))
                                                  (map (fn [j v]
                                                         [:div {:key j} [step {:selected?   v
                                                                               :playing?    (= i pointer)
                                                                               :step-number i
                                                                               :key j}]])
                                                       (range)))])
                                          (-> sequence sequence->steps))]]))
     :component-did-mount (fn [_]
                            (setup-midi!))}))

(r/render [root] (js/document.getElementById "app"))

(defonce go
         (play-sequence! 0 0 (->> (inf-seq (fn [] (->> (:sequence @app-state)
                                                       (sequence->steps)
                                                       (remove-empty-steps)))))))