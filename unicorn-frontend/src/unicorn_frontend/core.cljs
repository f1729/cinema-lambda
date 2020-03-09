(ns unicorn-frontend.core
  (:require [wscljs.client :as ws]
            [wscljs.format :as fmt]))


(def handlers {:on-message (fn [e] (prn (.-data e)))
               :on-open #(prn "Opening a new connection")
               :on-close #(prn "Closing a connection")})


(def socket (ws/create "ws://localhost:9090" handlers))

;; (js/console.log socket)

(js/console.log (ws/status socket))

(js/setTimeout #(ws/send socket {:command "check"} fmt/json) 2000)
;; (ws/send socket "ping")


(enable-console-print!)

(println ">>> This text is printed from src/unicorn-frontend/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!!"}))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
