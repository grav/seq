(ns seq.ui
  (:require [reagent.core :as r]))

(def cell-size 10)


(defn selector [keys vals current on-change]
  (prn "current" current)
  [:select {:value current :on-change #(on-change (.-value (.-target %)))}
   (map (fn [k v]
          (prn "oval" k)
          [:option {:key   (or k "nil")
                    :value k} v]) (cons nil keys) (cons "---" vals))])


(defn step [{:keys [selected? playing?]}]
  [:div {:style {:background-color (if playing? "yellow" "black")
                 :height           cell-size
                 :width            cell-size
                 :padding          (min 3 (max 1 (/ cell-size 10)))}}
   [:div {:style {:background-color (if selected? "red" "white")
                  :height           "100%"
                  :width            "100%"}}]])


(defn seq-view [{:keys [sequence step-clicked offset]}]
  [:div
   [:div {:style {:display "flex"}}
    (map (fn [[i vs]]
           [:div {:key i}
            (->> (reverse (take 16 (drop offset (range))))
                 (map (fn [j] (contains? (set vs) j)))
                 (map (fn [j v]
                        [:div {:key      j
                               :on-click #(step-clicked i j v)}
                         [step {:selected?   v
                                :playing?    false #_(= i pointer)
                                :step-number i
                                :key         j}]])
                      (reverse (take 16 (drop offset (range))))))])
         (->> (or sequence (repeat 16 [])) (map vector (range))))]])

(defn up-down [{:keys [text val handle-val-change min-val max-val]}]
  [:div {:style {:display "flex"}}

   [:div (str text ": " val)]
   [:button {:on-click #(handle-val-change (min max-val (inc val)))} "▲"]
   [:button {:on-click #(handle-val-change (max min-val (dec val)))} "▼"]])

(defn channel-changer [{:keys [channel handle-channel-change]}]
  [:div {:style {:display "flex"}}

   [:div (str "Channel: " channel)]
   [:div {:on-click #(handle-channel-change (min 15 (inc channel)))} "▲"]
   [:div {:on-click #(handle-channel-change (max 0 (dec channel)))} "▼"]])

(defn output-view [{:keys [sequence channel offset transpose]
                       :or   {channel   0
                              offset    0
                              transpose 0}}
                   [step-clicked
                    handle-val-change
                    nudge]]
  [:div
   [seq-view {:sequence     sequence
              :step-clicked step-clicked
              :channel      channel
              :offset       offset}]
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

(defn create-root [app-state {:keys [did-mount step-clicked handle-select handle-val-change nudge]}]
  (r/create-class
    {:reagent-render      (fn [] (let [{:keys [sequences pointer midi]} @app-state
                                       {:keys [outputs]} midi]
                                   [:div [:h3 (str "Seq - " (count outputs) " output devices connected")]
                                    [:div {:style {:display "flex"}}
                                     (for [o (:outputs midi)]
                                       (let [id (.-id o)
                                             sequence (get sequences id)]
                                         [:div {:style {:margin 10}
                                                :key   id}
                                          [:p (.-name o)]
                                          [output-view sequence
                                           (map #(partial % id) [step-clicked handle-val-change nudge])]]))]]))
     :component-did-mount (fn [_]
                            (did-mount))}))