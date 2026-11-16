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

### Rodando e testando nas IDEs (IntelliJ IDEA e Eclipse)

**O que instalar antes (igual para as duas IDEs)**
1. **JDK 21** (ex.: Temurin 21). O projeto não compila com JDK 17 ou anterior.
2. **MySQL ou MariaDB** rodando na porta 3306 (o do XAMPP serve). O banco `hospital` e as tabelas
   são criados sozinhos no primeiro acesso.
3. **Tomcat 9** (zip de tomcat.apache.org, descompactado numa pasta sem espaços, ex.: `C:\tomcat9`).
   O Tomcat 8.5 do XAMPP também funciona. **Tomcat 10 ou 11 não funciona** (usam `jakarta.servlet`).
4. Copie `src/main/webapp/WEB-INF/etc/config.local.example.json` para `config.local.json` na mesma
   pasta e ajuste usuário e senha do banco (no XAMPP: `root` sem senha).

Com o sistema no ar, entre com um dos usuários de demonstração (só para desenvolvimento; o sistema
pede a troca da senha no primeiro acesso): `recepcao@hospital.local` / `recepcao123`,
`enfermeiro@hospital.local` / `enfermeiro123`, `medico@hospital.local` / `medico123`,
`admin@hospital.local` / `admin123`.

#### IntelliJ IDEA (Community ou Ultimate)
1. **File › Open**, escolha o `pom.xml` e clique em **Open as Project** (confie no projeto).
2. **File › Project Structure › Project**: SDK = 21, Language level = 21.
3. Na aba **Maven** (lateral direita), clique em **Reload All Maven Projects**.
4. **Testes JUnit**: botão direito em `src/test/java` › **Run 'All Tests'**. Ou, na aba Maven,
   **Lifecycle › test**. Os testes de banco são pulados (não falham) se o MySQL estiver desligado.
5. **Suíte original** (`tests.RunAllTests`): abra `src/main/java/tests/RunAllTests.java` e clique no
   ▶ ao lado do `main`. Se aparecer `NoClassDefFoundError: javax/servlet/...`, edite a configuração
   (**Modify options › Add dependencies with "provided" scope to classpath**).
6. **Subir o sistema** (escolha uma opção):
   - **Ultimate**: **Run › Edit Configurations › + › Tomcat Server › Local**. Em *Application server*
     aponte a pasta do Tomcat 9. Na aba *Deployment*, **+ › Artifact › hospital-case-manager:war exploded**
     e defina *Application context* = `/hospital-case-manager`. Clique em **Run**.
   - **Community**: instale o plugin **Smart Tomcat** (Settings › Plugins › Marketplace). Depois
     **Run › Edit Configurations › + › Smart Tomcat**: *Tomcat Server* = pasta do Tomcat 9,
     *Deployment Directory* = `src/main/webapp`, *Context Path* = `/hospital-case-manager`. Clique em **Run**.
   - **Sem plugin**: rode `mvn package`, copie `target/hospital-case-manager.war` para `<tomcat>/webapps`,
     inicie com `bin/startup.bat` e, depois que a pasta `webapps/hospital-case-manager` aparecer, copie o
     seu `config.local.json` para `webapps/hospital-case-manager/WEB-INF/etc/` (o WAR não leva esse arquivo)
     e reinicie.
7. Abra `http://localhost:8080/hospital-case-manager/`. Se a porta 8080 estiver ocupada (ex.: Tomcat do
   XAMPP ligado), pare o outro Tomcat ou troque a porta na configuração.

#### Eclipse (Eclipse IDE for Enterprise Java and Web Developers, 2023-12 ou mais novo)
1. **Window › Preferences › Java › Installed JREs › Add › Standard VM**: aponte o JDK 21 e marque-o.
   Em **Java › Compiler**, *compliance level* = 21.
2. **File › Import › Maven › Existing Maven Projects**, escolha a pasta do projeto (a que tem o
   `pom.xml`) e **Finish**. O m2e configura sozinho o projeto web (pasta `src/main/webapp`).
   Se aparecer erro de configuração: botão direito no projeto › **Maven › Update Project** (Alt+F5).
3. **Servidor**: **Window › Show View › Servers** › link *"No servers are available..."* ›
   **Apache › Tomcat v9.0 Server** › *Tomcat installation directory* = pasta do Tomcat 9, *JRE* = JDK 21 ›
   **Next**, adicione `hospital-case-manager` › **Finish**. Clique com o botão direito no servidor › **Start**.
   (Atalho: botão direito no projeto › **Run As › Run on Server**.)
4. Abra `http://localhost:8080/hospital-case-manager/`. Ao alterar uma JSP, basta salvar e recarregar a
   página; ao alterar uma classe Java, o Eclipse republica (se não, botão direito no servidor › **Restart**).
5. **Testes JUnit**: botão direito em `src/test/java` › **Run As › JUnit Test** (JUnit 5). Ou botão
   direito no projeto › **Run As › Maven test**.
6. **Suíte original**: botão direito em `tests/RunAllTests.java` › **Run As › Java Application**.

**No computador da faculdade**, confira antes: versão do JDK (`java -version` precisa mostrar 21),
versão do Tomcat cadastrada no Eclipse (8.5 ou 9, nunca 10+) e se o MySQL do laboratório está ligado.
Se o laboratório só tiver Tomcat 10, baixe o zip do Tomcat 9 e aponte para ele: não precisa instalar.

#### Roteiro para testar o fluxo completo à mão
1. **Recepção**: cadastre um paciente, agende uma consulta para hoje e, na agenda, clique em
   **Registrar chegada**.
2. **Enfermeiro** (outra aba anônima): **Fila de triagem › Iniciar triagem**, preencha os sinais vitais,
   escolha a cor de prioridade e **Registrar e enviar ao médico**.
3. **Médico**: **Fila de atendimento › Chamar e atender**. Salve o rascunho, emita uma receita (abre a
   página de impressão com CRM), solicite um exame e **Finalize**. Depois disso o prontuário não muda mais;
   só aceita adendos.
4. Abra **Histórico** do paciente com cada perfil: o médico vê tudo, o enfermeiro vê triagens e alergias,
   a recepção vê só consultas e exames agendados, e o administrador não tem acesso clínico.
5. **Recepção**: numa consulta futura, **Remarcar**. A original fica cancelada ("Remarcada para…") e a
   nova fica ligada a ela; o paciente com e-mail recebe o aviso (em desenvolvimento, em
   `WEB-INF/storage/logs/mail.log`).
6. **Administrador**: **Profissionais › Cadastrar profissional** com "Criar um acesso novo": a senha
   temporária vai por e-mail e o primeiro login pede uma senha nova. Ajuste a **Disponibilidade
   semanal** do médico e confira tudo em **Auditoria** (trilha e e-mails enviados).

### Módulos do sistema hospitalar
| Módulo | Perfil | Telas |
|---|---|---|
| Pacientes | Recepção | `/pacientes` (busca, ficha, cadastro, inativação) |
| Agenda de consultas e exames | Recepção | `/agenda`, `/agenda/nova`, `/agenda/{id}/remarcar`, `/exames` |
| Triagem | Enfermagem | `/triagem` |
| Atendimento, prontuário, receitas, exames | Médico | `/atendimento` |
| Histórico do paciente | Médico, enfermagem, recepção (cada um com seu recorte) | `/pacientes/{id}/historico` |
| Profissionais, especialidades e disponibilidade | Administrador | `/profissionais`, `/especialidades` |
| Usuários e permissões | Administrador | `/admin` |
| Auditoria e e-mails enviados | Administrador | `/auditoria` |

### Validação dos diagramas PlantUML
```bash
mvn dependency:copy -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7 -DoutputDirectory=target/tools
java -jar target/tools/plantuml-1.2024.7.jar -syntax < src/main/webapp/WEB-INF/documentation/Banco_ER.puml
java -jar target/tools/plantuml-1.2024.7.jar -Playout=smetana -tpng -o ../../assets/images/diagrams src/main/webapp/WEB-INF/documentation/*.puml
```

### Mudanças no banco (migrações)
`WEB-INF/etc/script.sql` é a base: cria o banco do zero e as tabelas que faltarem. Qualquer mudança
posterior em tabela existente (`ALTER TABLE`, índice, ajuste de dados) deve virar um arquivo novo em
`WEB-INF/etc/migrations/NNN_descricao.sql`. O `DatabaseProvisioner` aplica cada arquivo uma única vez,
em ordem, e registra em `schema_migracoes`. Nunca edite uma migração já aplicada: crie outra.

### Tomcat compatível
O projeto usa `javax.servlet` (Servlet 3.1+): funciona no **Tomcat 8.5 e 9**, rodando com **JDK 21**.
Não funciona no Tomcat 10+ (que usa `jakarta.servlet`). No Tomcat 8.5 as JSPs são compiladas com
sintaxe Java 7 por padrão; por isso o código das JSPs evita recursos mais novos (ex.: `new ArrayList<>()`
dentro de expressão condicional).
