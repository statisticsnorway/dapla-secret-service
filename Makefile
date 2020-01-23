SHELL:=/usr/bin/env bash

.PHONY: default
.PHONY: start-db
.PHONY: stop-db
.PHONY: help

default: | help

start-db: ## Start the database
	docker-compose up -d --build postgres

stop-db: ## Stop the database and empty its data
	docker-compose rm -fsv postgres

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-45s\033[0m %s\n", $$1, $$2}'
