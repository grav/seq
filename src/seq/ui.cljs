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

(defn seq-view [{:keys [sequence step-clicked]}]
  [:div
   [:div {:style {:display "flex"}}
    (map (fn [[i vs]]
           [:div {:key i}
            (->> (reverse (range 16))
                 (map (fn [j] (contains? (set vs) j)))
                 (map (fn [j v]
                        [:div {:key      j
                               :on-click #(step-clicked i j v)}
                         [step {:selected?   v
                                :playing?    false #_(= i pointer)
                                :step-number i
                                :key         j}]])
                      (reverse (range 16))))])
         (->> (or sequence (repeat 16 [])) (map vector (range))))]])

(defn channel-changer [{:keys [channel handle-channel-change]}]
  [:div {:style {:display "flex"}}

   [:div (str "Channel: " channel)]
   [:div {:on-click #(handle-channel-change (min 15 (inc channel)))} "▲"]
   [:div {:on-click #(handle-channel-change (max 0 (dec channel)))} "▼"]])

(defn create-root [app-state {:keys [did-mount step-clicked handle-select handle-channel-change]}]
  (r/create-class
    {:reagent-render      (fn [] (let [{:keys [sequences pointer midi]} @app-state
                                       {:keys [outputs]} midi]
                                   [:div [:h3 (str "Seq - " (count outputs) " output devices connected")]
                                    [:div {:style {:display "flex"}}
                                     (for [o (:outputs midi)]
                                       (let [id (.-id o)
                                             {:keys [sequence channel]} (get sequences id)]
                                         [:div {:style {:margin 10}
                                                :key   id}
                                          [:p (.-name o)]

                                          [seq-view {:sequence     sequence
                                                     :step-clicked (partial step-clicked id)
                                                     :channel      channel}]
                                          [channel-changer {:channel (or channel 0) :handle-channel-change (partial handle-channel-change id)}]]))]]))
     :component-did-mount (fn [_]
                            (did-mount))}))