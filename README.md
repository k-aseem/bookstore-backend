# bookstore-backend

Backend service for the Aseem Bookstore application. This project provides a RESTful API (JAX-RS / Jersey)
that serves book, category and order data and is packaged as a WAR to be deployed to a Jakarta-compatible
servlet container (Tomcat 10+ / any Jakarta EE 9+ container).

**Quick facts**
- **Language:** Java 17
- **Build:** Gradle (wrapper provided)
- **Frameworks:** Jakarta Servlet / Jersey (JAX-RS)
- **Database:** MySQL (via container-managed JNDI DataSource `jdbc/AseemBookstore`)

**Table Of Contents**
- Project Overview
- Prerequisites
- Build
- Configure database (MySQL)
- Configure servlet container (Tomcat) and JNDI DataSource
- Deploy
- API Endpoints
- Project layout
- Development notes

---

**Project Overview**

This is the backend component for a bookstore web app. It exposes JSON REST endpoints used by the
frontend client (the companion `bookstore-client` folder in this workspace). The backend uses a
container-managed JNDI DataSource (lookups `java:comp/env/jdbc/AseemBookstore`) to obtain database
connections. SQL seed data is available under `src/main/resources/DB_script.mysql`.

**Prerequisites**
- Java 17 (JDK 17)
- MySQL server (or compatible) to host the bookstore schema and data
- Apache Tomcat 10.x (or another Jakarta EE 9/10 compatible servlet container). Tomcat 10 is
	recommended because this project uses `jakarta.*` packages.
- Internet access to download Gradle dependencies (the project uses the included Gradle wrapper)

---

**Build**

From the `bookstore-backend` project root run:

```bash
./gradlew clean build
```

The command produces a WAR file in `build/libs/` (e.g. `bookstore-backend.war`).

---

**Configure database (MySQL)**

1. Create a MySQL database and user for the application. Example (adjust user/password as needed):

```sql
CREATE DATABASE aseem_bookstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'bookuser'@'localhost' IDENTIFIED BY 'change_me';
GRANT ALL PRIVILEGES ON aseem_bookstore.* TO 'bookuser'@'localhost';
FLUSH PRIVILEGES;
```

2. Create the required tables. This repository includes a seed script `src/main/resources/DB_script.mysql`
	 which contains `INSERT` statements and `ALTER TABLE` commands. If you do not have the `CREATE TABLE`
	 statements in this repo, create the tables according to your schema requirements before running the
	 seed script.

3. Load the seed data (from project root):

```bash
mysql -u bookuser -p aseem_bookstore < src/main/resources/DB_script.mysql
```

Note: The repository's seed file contains `INSERT` statements only. If you need full schema DDL and it's
missing, create tables for `category`, `book`, `customer`, `order` and related tables before running the
seed script.

---

**Configure Tomcat (JNDI DataSource)**

This application expects a container-managed DataSource available under the JNDI name `jdbc/AseemBookstore`.
Below is an example `Context` configuration you can add to Tomcat's `conf/context.xml` or to an
application-specific context file (e.g. `conf/[engine]/[host]/[appName].xml`).

Example `context.xml` Resource snippet (edit username/password/url as appropriate):

```xml
<Resource name="jdbc/AseemBookstore"
					auth="Container"
					type="jakarta.sql.DataSource"
					factory="org.apache.tomcat.jdbc.pool.DataSourceFactory"
					driverClassName="com.mysql.cj.jdbc.Driver"
					url="jdbc:mysql://localhost:3306/aseem_bookstore?useSSL=false&serverTimezone=UTC"
					username="bookuser"
					password="change_me"
					maxTotal="20"
					maxIdle="10"
					maxWaitMillis="-1"/>
```

Important notes:
- Place the MySQL JDBC driver JAR (`mysql-connector-java-*.jar`) into Tomcat's `lib/` directory so the
	container can use it for the JNDI DataSource.
- The project JNDI lookup uses `java:comp/env/jdbc/AseemBookstore` (see `business.JdbcUtils`).

---

**Deploy**

1. Build the WAR as shown in the Build section.
2. Deploy the WAR to Tomcat by copying the produced WAR into `TOMCAT_HOME/webapps/` or using the
	 Tomcat Manager app.
3. Start Tomcat (or restart if already running). The application will be available at
	 `http://localhost:8080/` (default Tomcat port) and the API root is `/` (see API paths below).

If you prefer to run in an embedded container for development, consider adding a small Jetty/Servlet
plugin or running the application in an IDE that can hot-deploy a WAR to Tomcat.

---

**API Endpoints (JSON)**

The primary REST endpoints are implemented in `api.ApiResource`.

- GET /categories
	- Returns all categories
- GET /categories/{category-id}
	- Returns category by id
- GET /categories/name/{category-name}
	- Returns category by name
- GET /categories/{category-id}/books
	- Returns books in the category
- GET /categories/name/{category-name}/books
	- Returns books in the category (lookup by name)
- GET /categories/{category-id}/suggested-books?limit=N
	- Returns up to N suggested books from that category (default 3)
- GET /categories/name/{category-name}/suggested-books?limit=N
	- Same as above but category looked up by name
- GET /books/{book-id}
	- Returns a single book by id
- POST /orders
	- Places an order. Expects JSON payload matching `business.order.OrderForm` structure and
		returns `OrderDetails` on success.

Example curl:

```bash
curl -s http://localhost:8080/categories | jq '.'
curl -s http://localhost:8080/books/1001 | jq '.'

# Place an order (example payload must match server-side DTOs)
curl -X POST -H "Content-Type: application/json" --data @order.json http://localhost:8080/orders
```

---

**Project layout**

- `src/main/java/api` - JAX-RS resources (entry points)
- `src/main/java/business` - business services, DAOs and JDBC utilities
- `src/main/webapp` - static frontend files (served from the WAR), JSPs, web.xml
- `src/main/resources` - resource files (including `DB_script.mysql` seed file)

**Development notes**
- Database connections use a JNDI lookup name `jdbc/AseemBookstore` (see `business.JdbcUtils`).
- The project targets Jakarta (`jakarta.*` packages) and is packaged as a WAR. Use a compatible
	servlet container (Tomcat 10+).
- If you want to run unit tests:

```bash
./gradlew test
```

---

If anything in this README is unclear or you'd like me to add a sample Tomcat `context.xml`, a
Docker Compose setup for MySQL + Tomcat, or an example `order.json` payload for the `POST /orders`
call, tell me which option you prefer and I will add it.
