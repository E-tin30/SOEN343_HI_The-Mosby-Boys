#!/bin/sh
# Runs the backend and nginx in one container. If either one stops, the container stops
# so that the platform restarts it.
java -jar /app/app.jar &
nginx -g 'daemon off;' &
wait -n
exit $?
