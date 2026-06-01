.PHONY: compose-base up seed k6-build smoke load stress cupos circuit-breaker cache charts reset-inscription-circuit timeseries-dir timeseries-cupos timeseries-load timeseries-cache timeseries-circuit-breaker timeseries defendible all clean reports-dir

DOCKER ?= $(shell if command -v docker >/dev/null 2>&1; then command -v docker; elif [ -x /usr/local/bin/docker ]; then printf '/usr/local/bin/docker'; elif [ -x /opt/homebrew/bin/docker ]; then printf '/opt/homebrew/bin/docker'; else printf 'docker'; fi)
COMPOSE_BIN ?= $(shell if command -v docker-compose >/dev/null 2>&1; then command -v docker-compose; elif [ -x /opt/homebrew/bin/docker-compose ]; then printf '/opt/homebrew/bin/docker-compose'; elif $(DOCKER) compose version >/dev/null 2>&1; then printf '$(DOCKER) compose'; else printf 'docker-compose'; fi)
DOCKER_BUILDKIT ?= 0
COMPOSE_DOCKER_CLI_BUILD ?= 0
GENERATED_DIR = .generated
COMPOSE_SOURCE = ../docker-compose.e2e.yml
COMPOSE_BASE = $(GENERATED_DIR)/docker-compose.e2e.generated.yml
COMPOSE = $(COMPOSE_BIN) --project-directory .. -f $(COMPOSE_BASE) -f docker-compose.k6.yml
STACK_SERVICES = db-event db-inscription db-payment redis rabbitmq event-service payment-service inscription-service
K6 = $(COMPOSE) run --no-deps --rm k6
K6_NO_DASHBOARD = $(COMPOSE) run --no-deps --rm -e K6_WEB_DASHBOARD=false k6
K6_STRESS = $(COMPOSE) run --no-deps --rm -e K6_WEB_DASHBOARD=false -e K6_TOKEN_COUNT=100 k6

$(COMPOSE_BASE): $(COMPOSE_SOURCE)
	@mkdir -p $(GENERATED_DIR)
	@sed 's/payment-service:{/payment-service: {/' $(COMPOSE_SOURCE) > $(COMPOSE_BASE)

compose-base: $(COMPOSE_BASE)

reports-dir:
	@mkdir -p reports

timeseries-dir:
	@mkdir -p reports/timeseries reports/timeseries-charts

up: compose-base reports-dir
	DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) COMPOSE_DOCKER_CLI_BUILD=$(COMPOSE_DOCKER_CLI_BUILD) $(COMPOSE) up -d --build $(STACK_SERVICES)

seed: up
	$(COMPOSE) run --rm seed-load-test

k6-build: compose-base
	DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) COMPOSE_DOCKER_CLI_BUILD=$(COMPOSE_DOCKER_CLI_BUILD) $(COMPOSE) build k6

smoke: seed k6-build
	$(K6) run /load-tests/scripts/01-smoke-test.js
	@echo "Smoke test completado. Reporte: reports/01-smoke-test.html"

load: seed k6-build
	$(K6_NO_DASHBOARD) run /load-tests/scripts/02-load-test.js
	@echo "Load sostenido 150 VUs completado. Reporte: reports/02-load-test.html"

stress: seed k6-build
	$(K6_STRESS) run --compatibility-mode=base /load-tests/scripts/03-stress-test.js
	@echo "Stress test completado. Reporte: reports/03-stress-test.html"

cupos: seed k6-build
	$(K6) run /load-tests/scripts/04-cupos-concurrencia.js
	@echo "RNF-16 validado. Reporte: reports/04-cupos-concurrencia.html"

circuit-breaker: seed k6-build
	$(COMPOSE) stop event-service
	@set -e; trap '$(COMPOSE) start event-service' EXIT; $(K6) run /load-tests/scripts/05-circuit-breaker.js
	@echo "RNF-14 validado. Reporte: reports/05-circuit-breaker.html"

cache: seed k6-build
	$(COMPOSE) exec -T redis redis-cli FLUSHDB
	$(K6_NO_DASHBOARD) run /load-tests/scripts/06-cache-effectiveness.js
	@echo "Cache effectiveness validado. Reporte: reports/06-cache-effectiveness.html"

charts:
	python3 scripts/generate-evidence-charts.py
	@echo "Graficos generados en reports/charts/"

reset-inscription-circuit:
	$(COMPOSE) restart inscription-service
	@until $(COMPOSE) exec -T inscription-service wget -qO- http://localhost:8083/actuator/health | grep UP >/dev/null; do sleep 2; done

timeseries-cupos: seed k6-build timeseries-dir reset-inscription-circuit
	$(K6) run --out json=/reports/timeseries/04-cupos-concurrencia.jsonl /load-tests/scripts/04-cupos-concurrencia.js
	python3 scripts/generate-timeseries-charts.py

timeseries-load: seed k6-build timeseries-dir
	$(K6_NO_DASHBOARD) run --out json=/reports/timeseries/02-load-test.jsonl /load-tests/scripts/02-load-test.js
	python3 scripts/generate-timeseries-charts.py

timeseries-cache: seed k6-build timeseries-dir
	$(COMPOSE) exec -T redis redis-cli FLUSHDB
	$(K6_NO_DASHBOARD) run --out json=/reports/timeseries/06-cache-effectiveness.jsonl /load-tests/scripts/06-cache-effectiveness.js
	python3 scripts/generate-timeseries-charts.py

timeseries-circuit-breaker: seed k6-build timeseries-dir
	$(COMPOSE) stop event-service
	@set -e; trap '$(COMPOSE) start event-service' EXIT; $(K6) run --out json=/reports/timeseries/05-circuit-breaker.jsonl /load-tests/scripts/05-circuit-breaker.js
	python3 scripts/generate-timeseries-charts.py

timeseries: timeseries-cupos timeseries-load timeseries-cache timeseries-circuit-breaker
	@echo "Graficos temporales generados en reports/timeseries-charts/"

defendible: cupos load cache circuit-breaker charts
	@echo "Suite defendible ejecutada: cupos, load 150 VUs, cache y circuit breaker."

all: smoke cupos load cache circuit-breaker charts
	@echo "Suite K6 completa ejecutada."

clean: compose-base
	$(COMPOSE) down -v
