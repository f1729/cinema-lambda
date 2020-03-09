(ns unicorn.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.timer :refer :all]))

(defn fps-handler [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Pew, Pew!"})

(defn mail-man []
  "{\"Spongebob Narrator\": \"5 years later ...\"}")

(defn main-handler [req]
  {:status 200
   :headers {"Content-type" "text/json"}
   :body (mail-man)})

(defn general-handler [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "All hail General Zod!"})

(defroutes app-routes
  (GET "/" [] fps-handler)
  (POST "/postoffice" [] main-handler)
  (ANY "/anything-goes" [] general-handler)
  (route/not-found "You must be new here"))


(defn handler [request]
  (server/with-channel request channel
    (println channel)
    (loop [id 0]
      (when (< id 10)
        (schedule-task (* id 2000)
                       (server/send! channel (str "message from the server #" id) false))
        (recur (inc id))))
    (server/on-close channel (fn [status] (println "channel closed: " status)))
    (server/on-receive channel (fn [data] (println ">>>>>>" data) (server/send! channel data)))))


(defn -main
  "This is our app's entry point."
  [& args]
  (server/run-server handler {:port 9090})
  (println (str "Running at localhost in port: " ))
  ;; (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
   ;; (server/run-server #'app-routes {:port port})
  ;;  (println (str "Running at localhost in port: " port)))
  )
