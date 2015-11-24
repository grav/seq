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

(defn create-root [app-state {:keys [did-mount step-clicked handle-select]}]
  (r/create-class
    {:reagent-render      (fn [] (let [{:keys [sequence pointer midi]} @app-state]
                                   (prn "create-root")
                                   [:div

                                    (when-let [{:keys [in out inputs outputs]} midi]
                                      [:div
                                       [:div "Input: " [selector
                                                        (map #(.-id %) inputs)
                                                        (map #(.-name %) inputs)
                                                        (when in (.-id in))
                                                        (handle-select :inputs :in)]]
                                       [:div "Output: " [selector
                                                         (map #(.-id %) outputs)
                                                         (map #(.-name %) outputs)
                                                         (when out (.-id out))

                                                         (handle-select :outputs :out)]]])
                                    [:div {:style {:display "flex"}}
                                     (map (fn [[i vs]]
                                            [:div {:key i}
                                             (->> (reverse (range 16))
                                                  (map (fn [j] (contains? (set vs) j)))
                                                  (map (fn [j v]
                                                         [:div {:key      j
                                                                :on-click #(step-clicked i j v)}
                                                          [step {:selected?   v
                                                                 :playing?    (= i pointer)
                                                                 :step-number i
                                                                 :key         j}]])
                                                       (reverse (range 16))))])
                                          (->> sequence (map vector (range))))]]))
     :component-did-mount (fn [_]
                            (did-mount))}))