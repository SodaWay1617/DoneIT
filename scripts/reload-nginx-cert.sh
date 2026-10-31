#!/bin/sh
# Certbot deploy hook: only reload after a successful certificate renewal.
set -eu
docker exec doneit-nginx nginx -t
docker exec doneit-nginx nginx -s reload
