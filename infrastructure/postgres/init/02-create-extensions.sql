-- Extensiones necesarias en cada base de datos

\connect eventos_auth
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect eventos_event
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect eventos_inscription
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect eventos_payment
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect eventos_notification
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect eventos_certificate
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
