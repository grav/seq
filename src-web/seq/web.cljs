(ns seq.web
  (:require [reagent.core :as r]
            [seq.launchpad :as lp]
            [seq.util]
            [cljs.reader]
            [seq.core :as c]
            [seq.util :as util]))

(enable-console-print!)

#_(defn on-js-reload []
    ;; optionally touch your app-state to force rerendering depending on
    ;; your application
    (swap! c/app-state update-in [:__figwheel_counter] inc))

(def cell-size 10)


(defn selector [keys vals current on-change]
  [:select {:value current :on-change #(on-change (.-value (.-target %)))}
   (map (fn [k v]
          (prn "oval" k)
          [:option {:key   (or k "nil")
                    :value k} v]) (cons nil keys) (cons "---" vals))])


(defn step [{:keys [selected? playing?]}]
  [:div {:style {:background-color (when playing? "yellow")
                 :height           cell-size
                 :width            cell-size
                 :padding          (min 3 (max 1 (/ cell-size 10)))}}
   [:div {:style {:background-color (if selected? "red" "white")
                  :height           "100%"
                  :width            "100%"}}]])


(defn black-key? [j]
  (contains? #{1 3 6 8 10} (mod j 12)))

(defn ctrl-view [{:keys [sequence step-clicked]}]
  [:div
   [:span "Sustain"]
   (->> sequence
        (map-indexed (fn [i ns]
                       (for [{:keys [note sustain]} ns]
                         [:div {:key note} (str i "," note ": ")
                          [:input {:type      :range
                                   :min       0 :max 100
                                   :value     (* 100 sustain)
                                   :step      1
                                   :on-change #(let [v (/ (js/parseInt (.-value (.-target %))) 100)]
                                                 (step-clicked i note :edit v))}]
                          sustain]))))])

(defn seq-view [{:keys [sequence step-clicked offset position]}]
  [:div
   [:div {:style {:display "flex"}}
    (->> (or sequence (repeat 16 []))
         (map vector (range))
         (map (fn [[i vs]]
                [:div {:key i}
                 (->> (reverse (take 16 (drop offset (range))))
                      (map (fn [j] ((set (map :note vs)) j)))
                      (map (fn [j selected]
                             (let [f #(step-clicked i j (if selected :off :on) 0.3)]
                               [:div {:key            j
                                      :on-click       f
                                      :on-touch-start f
                                      :style          {:background-color (if (black-key? j)
                                                                           "#777"
                                                                           :black)}}
                                [step {:selected?   selected
                                       :playing?    (= i position)
                                       :step-number i
                                       :key         j}]]))
                           (reverse (take 16 (drop offset (range))))))])))]])

(defn up-down [{:keys [text val handle-val-change min-val max-val]}]
  [:div {:style {:display "flex"}}

   [:div (str text ": " val)]
   [:button {:on-click #(handle-val-change (min max-val (inc val)))} "▲"]
   [:button {:on-click #(handle-val-change (max min-val (dec val)))} "▼"]])

(defn output-view [{:keys [sequence channel offset transpose]
                    :or   {channel   0
                           offset    0
                           transpose 0}}
                   position
                   [step-clicked
                    handle-val-change
                    nudge]]
  [:div
   [ctrl-view {:sequence     sequence
               :step-clicked step-clicked}]
   [seq-view {:sequence     sequence
              :step-clicked step-clicked
              :channel      channel
              :offset       offset
              :position     position}]
   [:div (map (fn [[key val min max]]
                [:div {:key key}
                 [up-down {:text              (name key)
                           :val               val
                           :handle-val-change (partial handle-val-change key)
                           :min-val           min
                           :max-val           max}]])
              [[:channel channel 0 15]
               [:offset offset 0 40]
               [:transpose transpose -24 24]])
    [:div "Nudge"
     [:button {:on-click #(nudge -1)} "←"]
     [:button {:on-click #(nudge 1)} "→"]]]])

(defn refresh-sessions! [state]
  (swap! state assoc :sessions (-> js/localStorage
                                   (seq.util/js-maplike->map)
                                   keys)))

(defn session-view []
  (let [state (r/atom {})] (r/create-class
                             {:reagent-render      (fn [{:keys [handle-select handle-save]}]
                                                     (let [{:keys [sessions name]} @state]
                                                       [:div [:h3 "Saved sessions"]
                                                        [:ul (map (fn [n] [:li {:key n}
                                                                           [:a {:href     "#"
                                                                                :on-click #(handle-select n)} n]]) sessions)]
                                                        [:div "Save: "
                                                         [:input {:value     name
                                                                  :on-change #(swap! state assoc :name (.-target.value %))}]
                                                         [:button {:on-click (fn [_]
                                                                               (handle-save name)
                                                                               (refresh-sessions! state))} "Save"]]]))
                              :component-did-mount (refresh-sessions! state)})))

(defn decay-view [{:keys [sustain handle-change]}]
  [:div
   "Sustain"
   [:input {:type      "range"
            :min       0
            :max       0.5
            :value     sustain
            :step      0.01
            :on-change #(handle-change (js/parseFloat (.-target.value %)))}]
   (str sustain " s")])

(defn controller-view [controllers {:keys [modifiers]}]
  [:div
   [:h3 "Connected controllers"]
   [:ul (for [c controllers]
          [:li {:key (.-id c)}
           (.-name c)])]
   (for [[n enabled?] modifiers]
     (when enabled? [:span {:style {:margin 5}
                            :key   (str n)} (name n)]))])

(defn main-view [{:keys [tracks position step-clicked handle-val-change nudge selected-track]}]
  [:div [:h3 (str "Seq - " (count tracks) " tracks")]
   [:div {:style {:display "flex"}}
    (for [[{:keys [id name sequence]} idx] (map vector tracks (range))]
      [:div {:style {:margin     10
                     :padding    2
                     :background (when (= idx selected-track) :green)}
             :key   id}
       [:div {:style {:background-color :white
                      :padding          2}}
        [:div name]
        [output-view sequence
         position
         (map #(partial % id) [step-clicked handle-val-change nudge])]]])]])

(def latency 1)

(defn ding
  [out channel v start t]
  (let [t1 (* 1000 start)
        t2 (* 1000 (+ start t))]
    (.send out #js [(+ channel 144), v, 0x30] t1)
    (.send out #js [(+ channel 128), v, 0x00] t2)))

(defn play-repeatedly
  ([init]
   (play-repeatedly init {:beat 0 :time 0}))
  ([{:keys [app-state play-sequence! now-fn]} {:keys [beat time]}]

   (let [{:keys [position notes]
          :as   res} (play-sequence! latency
                                     @app-state
                                     (/ (now-fn) 1000)
                                     beat time)]
     (doseq [n notes]
       (apply ding n))
     (swap! app-state assoc :position position)
     (js/setTimeout #(play-repeatedly {:app-state      app-state
                                       :now-fn         now-fn
                                       :play-sequence! play-sequence!}
                                      res)
                    (* latency 1000)))))

(defn root-view [app-state {:keys [setup-midi! play-sequence! step-clicked handle-midi-select
                                   handle-val-change nudge]}]
  (r/create-class
    {:reagent-render      (fn []
                            (let [{:keys [position sustain midi launchpad sequences]} @app-state
                                  controllers (filter lp/is-launchpad? (:inputs midi))
                                  tracks (->> (:outputs midi)
                                              (remove lp/is-launchpad?)
                                              (util/tracks sequences))]
                              [:div
                               [main-view {:tracks            tracks
                                           :selected-track    (when-not (empty? controllers)
                                                                (or (min (:index launchpad) (dec (count tracks)))
                                                                    0))

                                           :position          position
                                           :step-clicked      step-clicked
                                           :handle-select     handle-midi-select
                                           :handle-val-change handle-val-change
                                           :nudge             nudge}]
                               [decay-view {:sustain       sustain
                                            :handle-change #(swap! app-state assoc :sustain %)}]
                               [session-view {:handle-select #(->> (aget js/localStorage %)
                                                                   (cljs.reader/read-string)
                                                                   (swap! app-state assoc :sequences))
                                              :handle-save   #(->> (with-out-str (prn (:sequences @app-state)))
                                                                   (aset js/localStorage %))}]
                               (when-not (empty? controllers)
                                 [controller-view controllers launchpad])]))
     :component-did-mount (fn []
                            (setup-midi!)
                            (play-repeatedly {:app-state      app-state
                                              :now-fn         #(.now (.-performance js/window))
                                              :play-sequence! play-sequence!}))}))

(defonce app-state (r/atom nil))

(defn main []
  (let []
    (reagent.core/render [root-view app-state {:setup-midi!        (partial c/setup-midi! app-state #(js/navigator.requestMIDIAccess) #(.now (.-performance js/window)))
                                               :play-sequence!     c/play-sequence!
                                               :step-clicked       (partial c/step-clicked app-state)
                                               :handle-midi-select (partial c/handle-midi-select app-state)
                                               :handle-val-change  (partial c/handle-val-change app-state)
                                               :nudge              (partial c/nudge app-state)}]
                         (js/document.getElementById "app"))))
