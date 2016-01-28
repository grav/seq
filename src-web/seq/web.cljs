(ns ^:figwheel-always seq.web
  (:require [reagent.core :as r]
            [seq.launchpad :as lp]
            [seq.util]
            [cljs.reader]
            [seq.core :as c]))

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

(defn seq-view [{:keys [sequence step-clicked offset position]}]
  [:div
   [:div {:style {:display "flex"}}
    (map (fn [[i vs]]
           [:div {:key i}
            (->> (reverse (take 16 (drop offset (range))))
                 (map (fn [j] (contains? (set vs) j)))
                 (map (fn [j selected]
                        [:div {:key            j
                               :on-click       #(step-clicked i j)
                               :on-touch-start #(step-clicked i j)
                               :style          {:background-color (if (black-key? j)
                                                                    "#777"
                                                                    :black)}}
                         [step {:selected?   selected
                                :playing?    (= i position)
                                :step-number i
                                :key         j}]])
                      (reverse (take 16 (drop offset (range))))))])
         (->> (or sequence (repeat 16 [])) (map vector (range))))]])

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
     (when enabled? [:span {:style {:margin 5}} (name n)]))])

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

(defn root-view [app-state {:keys [setup-midi! step-clicked handle-midi-select
                                   handle-val-change nudge]}]
  (r/create-class
    {:reagent-render      (fn []
                            (let [{:keys [position sustain midi launchpad]} @app-state
                                  controllers (filter lp/is-launchpad? (:inputs midi))
                                  tracks (c/tracks @app-state)]
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
     :component-did-mount setup-midi!}))

(defn main []
  (reagent.core/render [root-view c/app-state {:setup-midi!        c/setup-midi!
                                               :step-clicked       c/step-clicked
                                               :handle-midi-select c/handle-midi-select
                                               :handle-val-change  c/handle-val-change
                                               :nudge              c/nudge}]
                       (js/document.getElementById "app")))
