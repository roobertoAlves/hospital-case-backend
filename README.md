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

### Formato do projeto: Dynamic Web Project (sem Maven)
Este repositório é o **backend do Asklep** e segue o formato do projeto original do professor: um
**Dynamic Web Project** do Eclipse.
- `src/main/java`: código Java.
- `src/main/webapp`: arquivos web, com as bibliotecas em `WEB-INF/lib`.
- `src/test/java`: testes JUnit 5.
- `lib/test`: JUnit (só testes; não vai para o Tomcat).
- `lib/compile`: API de Servlet, só para compilar fora do Eclipse. No servidor, quem fornece é o Tomcat.
- `.project`, `.classpath` e `.settings/`: configuração do Eclipse. Vão para o Git de propósito.

O frontend (React) fica em outro repositório, **hospital-case-frontend**.

### Testes automatizados pela linha de comando (sem IDE, sem Maven)
Precisa do JDK 21 no PATH (`java -version` mostra 21) e do MySQL ligado (os testes de banco criam um
banco temporário e o apagam no fim).
```bash
testar.bat           # Windows (cmd ou PowerShell)
./testar.sh          # Linux, macOS ou Git Bash
./testar.sh models   # só um pacote
```
A suíte original do framework continua disponível depois de rodar o script:
```bash
java -cp "build/classes;lib/compile/*;src/main/webapp/WEB-INF/lib/*" tests.RunAllTests
```
(no Linux/macOS, troque `;` por `:`)

### Configuração local (credenciais por máquina)
O `config.json` versionado não deve receber senhas reais. Copie
`src/main/webapp/WEB-INF/etc/config.local.example.json` para `config.local.json` (mesma pasta,
fora do git) e ajuste banco, `app.url` e SMTP. As chaves desse arquivo sobrepõem as do `config.json`.
O banco padrão chama-se `hospital` e é criado automaticamente pelo `DatabaseProvisioner`, já com os
dados fictícios de demonstração (migrações 002 e 003).

### Rodando e testando nas IDEs (Eclipse e IntelliJ IDEA)

**O que instalar antes (igual para as duas IDEs)**
1. **JDK 21** (ex.: Temurin 21). O projeto não compila com JDK 17 ou anterior.
2. **MySQL ou MariaDB** rodando na porta 3306 (o do XAMPP serve).
3. **Tomcat 9 ou 8.5**: é só uma pasta, não tem instalador.
   - O do XAMPP (`C:\xampp\tomcat`, versão 8.5) já serve.
   - Também pode ser o zip do Tomcat 9 (tomcat.apache.org), descompactado numa pasta sem espaços, como `C:\tomcat9`.
   - **Tomcat 10 ou 11 não funciona**, porque usam `jakarta.servlet`.
4. Copie `config.local.example.json` para `config.local.json` (veja acima) e ajuste usuário e senha
   do banco (no XAMPP: `root` sem senha).

Usuários de demonstração (só para desenvolvimento):

| Perfil | E-mail | Senha |
|---|---|---|
| Recepção | `recepcao@hospital.local` | `recepcao123` |
| Enfermagem | `enfermeiro@hospital.local` | `enfermeiro123` |
| Médico | `medico@hospital.local` | `medico123` |
| Administrador | `admin@hospital.local` | `admin123` |

A migração 003 acrescenta mais logins de demonstração.

#### Eclipse (Eclipse IDE for Enterprise Java and Web Developers, 2023-12 ou mais novo)
1. **Window › Preferences › Java › Installed JREs › Add › Standard VM**: aponte o JDK 21 e marque-o.
2. **File › Import › General › Existing Projects into Workspace**. Em *Select root directory*, escolha
   a pasta do repositório e marque `hospital-case-backend`. Clique em **Finish**.
   - Não marque *Copy projects into workspace*.
   - O projeto já vem configurado como Dynamic Web Project (Java 21, Dynamic Web Module 3.1).
3. **Servidor**: **Window › Show View › Servers**. Clique no link *"No servers are available..."* e
   escolha **Apache › Tomcat v9.0 Server** (ou v8.5).
   - Em *Tomcat installation directory*, aponte a pasta do Tomcat. Se não tiver uma, o botão
     **Download and Install...** dessa mesma tela baixa o Tomcat para você.
   - Em *JRE*, escolha o JDK 21.
   - Clique em **Next**, adicione `hospital-case-backend` e clique em **Finish**.
4. Botão direito no servidor › **Start** (ou botão direito no projeto › **Run As › Run on Server**).
   Abra `http://localhost:8080/hospital-case-backend/`.
   - Ao alterar uma JSP, basta salvar e recarregar.
   - Ao alterar uma classe Java, o Eclipse republica sozinho. Se não, clique com o botão direito no
     servidor › **Restart**.
5. **Se aparecer erro de API de Servlet** (ex.: *"javax.servlet cannot be resolved"* numa JSP):
   botão direito no projeto › **Properties › Targeted Runtimes**, marque o Tomcat e clique em **Apply**.
6. **Testes JUnit**: botão direito em `src/test/java` › **Run As › JUnit Test**.
7. **Suíte original**: botão direito em `tests/RunAllTests.java` › **Run As › Java Application**.

#### IntelliJ IDEA
O IntelliJ não traz um Tomcat embutido: ele só usa uma pasta do Tomcat que já esteja no seu PC (o do
XAMPP serve).
1. **File › New › Project from Existing Sources**, escolha a pasta do repositório.
   - Selecione **Import project from external model › Eclipse** e avance até o fim.
   - Ele lê o `.project` e o `.classpath`.
2. **File › Project Structure**:
   - **Project**: SDK = 21, Language level = 21.
   - **Modules**: confira se `src/test/java` está marcado como *Tests* (verde).
   - Se o IntelliJ não achar as bibliotecas, adicione `src/main/webapp/WEB-INF/lib`, `lib/compile`
     e `lib/test` em **Libraries**.
3. **Testes JUnit**: botão direito em `src/test/java` › **Run 'All Tests'**.
4. **Subir o sistema** (escolha uma opção):
   - **Ultimate** (gratuito com e-mail institucional pelo programa educacional da JetBrains):
     1. Em **Project Structure › Facets › + › Web**, aponte *Web Resource Directory* = `src/main/webapp`.
     2. Em **Artifacts › + › Web Application: Exploded › From Modules**, escolha o módulo.
     3. Em **Run › Edit Configurations › + › Tomcat Server › Local**, aponte a pasta do Tomcat.
     4. Na aba *Deployment*, clique em **+ › Artifact**. Em *Application context*, use `/hospital-case-backend`.
   - **Community**:
     1. Instale o plugin **Smart Tomcat** (Settings › Plugins).
     2. Em **Run › Edit Configurations › + › Smart Tomcat**, preencha *Tomcat Server* = pasta do Tomcat,
        *Deployment Directory* = `src/main/webapp` e *Context Path* = `/hospital-case-backend`.
5. Abra `http://localhost:8080/hospital-case-backend/`. Se a porta 8080 estiver ocupada (ex.: Tomcat
   do XAMPP ligado pelo painel), pare o outro Tomcat ou troque a porta.

**No computador da faculdade**, confira antes:
- `java -version` mostra 21;
- o Tomcat cadastrado no Eclipse é 8.5 ou 9, nunca 10 ou mais novo;
- o MySQL do laboratório está ligado.

Se o laboratório só tiver Tomcat 10, use o **Download and Install...** do Eclipse e escolha o Tomcat 9.

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

### Portal do paciente (Fase extra)
- **Sem login** (`/api/publico/catalogo`): especialidades e médicos, exames (com preparo e se exigem pedido) e vacinas.
- **Cadastro** (`POST /api/portal/cadastro`):
  - CPF novo: cria a ficha e a conta já ligadas.
  - CPF que já tem ficha: a conta só é ligada com o código de 6 dígitos enviado ao **e-mail da ficha** (vale 15 minutos e aceita 5 tentativas). Sem e-mail na ficha, a recepção liga a conta.
- **Paciente logado** (grupo `paciente`, rotas `/api/portal/*`, só os próprios dados):
  - consultas: marcar, confirmar presença, remarcar e cancelar;
  - exames: os que exigem pedido só com o pedido anexado (PDF, JPG ou PNG, até 5 MB) ou com uma solicitação do médico no sistema;
  - vacinas: agendamento conforme o esquema de doses e cartão de vacinação;
  - receitas.
- **Equipe**:
  - recepção: confere os pedidos (recusar cancela o exame e avisa o paciente) e agenda vacinas;
  - enfermagem: registra a aplicação, com lote obrigatório. O registro não pode ser alterado depois.
- **Login de demonstração**: `paciente@hospital.local` / `paciente123` (paciente Ana, migrações 004 e 005).

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
# plantuml-1.2024.7.jar: baixe em https://plantuml.com/download (fica fora do repositório)
java -jar plantuml-1.2024.7.jar -syntax < src/main/webapp/WEB-INF/documentation/Banco_ER.puml
java -jar plantuml-1.2024.7.jar -Playout=smetana -tpng -o ../../assets/images/diagrams src/main/webapp/WEB-INF/documentation/*.puml
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
