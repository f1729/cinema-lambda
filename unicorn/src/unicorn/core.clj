(ns unicorn.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.util.response :refer [response]]
            [org.httpkit.timer :refer :all]))

;; API

(defn fps-handler [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Pew, Pew!"})

(defn mail-man []
  "{\"Spongebob Narrator\": \"5 years later ...\"}")

(defn main-handler
  [req]
  {:status 200
   :headers {"Content-type" "text/json"}
   :body (mail-man)})

(defn general-handler
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "All hail General Zod!"})

;; This endpoint can receive json

(defn save-configuration
  [req]
  (println (:body req))
  {:status 200 :body (:body req)})

;; WebSockets

(def clients (atom {}))

(defn send-to-all [msg]
  (doseq [client (keys @clients)]
    (server/send! client msg)))

(defn on-receive-handler [data channel]
  (case (:command (json/read-str data :key-fn keyword))
    "check" (server/send! channel "Hola!")))

(schedule-task 10000 (send-to-all "change-video"))

(defn ws-handler [request]
  (server/with-channel request channel
    ;; (println channel)
    (swap! clients assoc channel true)
    (loop [id 0]
      (when (< id 10)
        (schedule-task (* id 2000)
                       (server/send! channel (str "message from the server #" id) false))
        (recur (inc id))))
    (server/on-close channel (fn [status] (println "channel closed: " status)))
    (server/on-receive channel #(on-receive-handler % channel))))

;; Router

(defroutes app-routes
  (GET "/" [] fps-handler)
  (GET "/ws" [] ws-handler)
  (POST "/postoffice" [] main-handler)
  (ANY "/anything-goes" [] general-handler)
  (POST "/save-configuration" [] save-configuration)
  (route/not-found "You must be new here"))

(defn -main
  "This is our app's entry point."
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "9090"))]
    (server/run-server (-> #'app-routes
                           (#(wrap-json-body % {:keywords? true}))
                           wrap-json-response) {:port port})
    (println (str "Running at localhost in port: " port))))
