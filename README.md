# ZTF_J2EE

> **Framework Java EE (Servlet / JSP / JDBC) Enxuto, Didático e Seguro por Padrão**

### Autor Oficial
- **Professor Drº Cleber S. Oliveira**

### Agradecimento Especial
- **Professora Drª Marcia Aparecida Silva Bissaco**

---

### Principais Características
- **Hierarquia J2EE Padronizada (`controllers.Controller extends HttpServlet`)**: Todos os controladores herdam de `Controller`, que estende `HttpServlet` e implementa o método despachante `handle(action, request, response)` além de helpers integrados para views JSP e respostas JSON.
- **Roteamento Declarativo (`Router` + `WEB-INF/etc/routes.json`)**: Rotas, verbos HTTP, guarda de sessão (`HttpSession`) e controle de acesso (ACL por grupos e permissões) declarados em JSON.
- **Configuração Centralizada (`Config` + `WEB-INF/etc/config.json`)**: Leitura em memória com notação de ponto (`dot notation`) e respostas REST/AJAX padronizadas via `JsonResponse` (Gson).
- **Resolução 100% Dinâmica de URL (`Url`)**: `contextPath` (nome da pasta do projeto), protocolo, domínio e porta são extraídos diretamente da requisição HTTP (`HttpServletRequest`), permitindo renomear o projeto ou mudar de servidor/porta sem alterar código.
- **Auto-Provisionamento no Boot (`DatabaseProvisioner`)**: O banco de dados e suas 7 tabelas (`WEB-INF/etc/script.sql`) são conferidos e criados automaticamente no boot.
- **Query Builder (`DBQuery`)**: Consultas SQL fluentes com `PreparedStatement` e travas de segurança contra `UPDATE`/`DELETE` sem `WHERE`.
- **Segurança Criptográfica**: Senhas com BCrypt (custo 12) + Salt + HMAC-SHA256 Pepper e uploads 100% em Base64 com ACL granular.

---

### Manual Completo de Uso e Material Didático (Aulas das Classes + Diagramas UML)
Consulte o guia didático e passo a passo em:
- 👉 **[src/main/webapp/manual.html](src/main/webapp/manual.html)** (Manual Interativo HTML com zoom nos diagramas)

---

### Compilando e Executando os Testes Automatizados
```bash
# Compilar todas as classes do projeto:
javac --release 21 -encoding UTF-8 \
  -cp "src/main/webapp/WEB-INF/lib/*:/home/zait/PortableApps/apache-tomcat-9.0.121/lib/servlet-api.jar" \
  -d build/classes $(find src/main/java -name "*.java")

# Executar a suíte completa de testes automatizados:
java -cp "build/classes:src/main/webapp/WEB-INF/lib/*:/home/zait/PortableApps/apache-tomcat-9.0.121/lib/servlet-api.jar" \
  tests.RunAllTests
```

---

### Configuração local (credenciais por máquina)
O `config.json` versionado não deve receber senhas reais. Copie
`src/main/webapp/WEB-INF/etc/config.local.example.json` para `config.local.json` (mesma pasta,
fora do git) e ajuste banco, `app.url` e SMTP. As chaves desse arquivo sobrepõem as do `config.json`.
O banco padrão chama-se `hospital` e é criado automaticamente pelo `DatabaseProvisioner`.

### Build e testes com Maven (Eclipse e IntelliJ importam o mesmo `pom.xml`)
```bash
mvn test        # testes JUnit 5 (unitários + integração; os de banco são ignorados se o MySQL não responder)
mvn package     # gera target/hospital-case-manager.war (sem config.local.json, logs e uploads)
```

A suíte original continua disponível (após `mvn compile`):
```bash
java -cp "target/classes;src/main/webapp/WEB-INF/lib/*;<TOMCAT>/lib/servlet-api.jar" tests.RunAllTests
```
(no Linux/macOS, troque `;` por `:`)

### Validação dos diagramas PlantUML
```bash
mvn dependency:copy -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7 -DoutputDirectory=target/tools
java -jar target/tools/plantuml-1.2024.7.jar -syntax < src/main/webapp/WEB-INF/documentation/Banco_ER.puml
java -jar target/tools/plantuml-1.2024.7.jar -Playout=smetana -tpng -o ../../assets/images/diagrams src/main/webapp/WEB-INF/documentation/*.puml
```
