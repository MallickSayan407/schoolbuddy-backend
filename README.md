# SchoolBuddy.ai 🎓

**SchoolBuddy.ai** is a full-stack, multimodal AI educational tutor
designed for students from **Grade 1 to Grade 10**.

It accepts **text, images, or both** and provides grade-aware
educational assistance across **Math, Science, and English**. The system
combines a Next.js/React frontend, a Java/Spring Boot backend, MySQL
conversation persistence, and a multimodal LLM provider layer with
fallback handling.

## 🌐 Live Application

**Frontend:** https://schoolbuddy-frontend.vercel.app/

**Backend:** https://schoolbuddy-backend-6on8.onrender.com/

## 📦 Repositories

-   **Frontend:**
    https://github.com/MallickSayan407/schoolbuddy-frontend
-   **Backend:** https://github.com/MallickSayan407/schoolbuddy-backend

------------------------------------------------------------------------

# ✨ Features

### 🎓 Grade-aware tutoring

Supports **Grade 1--10** and adapts:

-   Vocabulary
-   Explanation depth
-   Examples
-   Mathematical difficulty
-   Reasoning depth
-   Terminology

### 📚 Subjects

-   Mathematics
-   Science
-   English

### 🧠 Learning modes

-   **Explain** --- teaches a concept clearly
-   **Solve** --- works through a problem step by step
-   **Hint** --- provides guidance without immediately revealing the
    answer
-   **Simplify** --- converts difficult explanations into easier
    language
-   **Practice** --- generates practice questions
-   **Quiz** --- provides interactive question-based learning

### 🖼️ Multimodal image questions

Students can upload:

-   JPG
-   JPEG
-   PNG
-   WebP

The image is converted to Base64 on the frontend and sent to the
backend, where it can be passed to a vision-capable LLM.

Examples include:

-   Photograph of a textbook problem
-   Mathematical equation
-   Science diagram
-   Word problem
-   Question screenshot

### 💬 Conversation context

SchoolBuddy stores:

-   Conversation metadata
-   User messages
-   Assistant responses
-   Grade
-   Subject
-   Conversation timestamps

Previous messages are retrieved and supplied as context for subsequent
questions.

### 🧮 Mathematical formatting

AI mathematical responses are rendered using Markdown + KaTeX.

Examples:

-   Inline mathematics: `$a = b + c$`
-   Display equations: `$$F = ma$$`
-   Fractions: `$\frac{3}{4}$`

A backend response-normalization layer also handles common cases where
the model incorrectly places mathematical expressions inside Markdown
code backticks.

### 🔄 AI fallback architecture

The backend uses a provider abstraction so the application is not
tightly coupled to one LLM implementation.

The primary Gemini integration supports a **multi-model cascade**. If
one configured Gemini model is unavailable or rate-limited, the next
configured model is attempted.

Additional provider clients are available as fallback providers for
resilience.

### 🛡️ Educational safety behavior

The prompt layer instructs the tutor to:

-   Stay age appropriate
-   Avoid inventing information from unclear images
-   Acknowledge uncertainty
-   Explain rather than blindly provide answers
-   Give useful hints without immediately revealing solutions
-   Encourage learning and independent problem solving
-   Avoid unnecessary decorative content

------------------------------------------------------------------------

# 🏗️ High-Level Architecture

``` text
                         ┌──────────────────────────┐
                         │       Student            │
                         │  Text / Image / Both     │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │ Next.js + React Frontend │
                         │                          │
                         │ Grade / Subject / Mode   │
                         │ Image Upload             │
                         │ Conversation UI          │
                         │ Markdown + KaTeX         │
                         └────────────┬─────────────┘
                                      │ HTTPS / JSON
                                      ▼
                     ┌────────────────────────────────┐
                     │      Spring Boot Backend       │
                     │                                │
                     │  ChatController                │
                     │        │                       │
                     │  ChatService                  │
                     │        │                       │
                     │  PromptService                │
                     │        │                       │
                     │  LLMClient abstraction        │
                     └───────────────┬────────────────┘
                                     │
                  ┌──────────────────┼──────────────────┐
                  │                  │                  │
                  ▼                  ▼                  ▼
        ┌────────────────┐  ┌─────────────────┐  ┌─────────────────┐
        │ Gemini Cascade │  │ Other Providers │  │ Mock Fallback   │
        │ Multi-model    │  │ Sarvam/OpenAI/  │  │ Development     │
        │ routing        │  │ OpenRouter      │  │ safety net      │
        └────────┬───────┘  └─────────────────┘  └─────────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │ Multimodal LLM     │
        │ Text + Image       │
        └────────────────────┘

                         Backend
                            │
                            ▼
                  ┌───────────────────┐
                  │       MySQL       │
                  │                   │
                  │ conversations     │
                  │ messages          │
                  └───────────────────┘
```

------------------------------------------------------------------------

# 🛠️ Technology Stack

## Frontend

  Technology       Purpose
  ---------------- --------------------------------
  Next.js          React application framework
  React            User interface
  TypeScript       Type-safe frontend development
  Tailwind CSS     UI styling
  React Markdown   Markdown response rendering
  remark-math      Markdown math parsing
  rehype-katex     Mathematical rendering
  KaTeX            Fast mathematical typesetting

## Backend

  Technology           Purpose
  -------------------- --------------------------
  Java 21              Backend language/runtime
  Spring Boot 4.1.1    Application framework
  Spring Web           REST APIs
  Spring Data JPA      Database access
  Hibernate            ORM
  Gradle               Build automation
  Jakarta Validation   Request validation
  Apache Tomcat        Embedded web server

## Database

-   MySQL
-   JPA/Hibernate
-   HikariCP connection pooling

## AI

-   Google Gemini multimodal API
-   Multi-model Gemini fallback
-   Provider abstraction through `LLMClient`
-   Prompt engineering
-   Grade-aware prompting
-   Multimodal image reasoning
-   Response normalization for mathematical formatting

## Deployment

  Component        Platform
  ---------------- -------------
  Frontend         Vercel
  Backend          Render
  Database         Aiven MySQL
  Source control   GitHub

------------------------------------------------------------------------

# 🧠 AI Architecture

SchoolBuddy does not train a custom language model.

Instead, it uses a hosted multimodal LLM through a backend-controlled
provider abstraction.

The backend constructs three important pieces of information:

``` text
Student Request
      │
      ├── Grade
      ├── Subject
      ├── Learning Mode
      ├── Current Question
      └── Optional Image
             │
             ▼
       PromptService
             │
             ├── Grade guidance
             ├── Learning principles
             ├── Image instructions
             ├── Math/science formatting
             └── Mode-specific behavior
             │
             ▼
        LLMClient
             │
             ▼
       AI Provider Layer
             │
             ▼
          Response
             │
             ▼
  Math/response normalization
             │
             ▼
       Stored + returned
```

The application therefore separates:

1.  **Application logic**
2.  **Prompt construction**
3.  **LLM provider integration**
4.  **Response normalization**
5.  **Conversation persistence**

This makes the AI layer easier to change without rewriting the rest of
the application.

------------------------------------------------------------------------

# 🔄 Gemini Multi-Model Fallback

The Gemini client supports a configured ordered list of models.

Conceptually:

``` text
Request
   │
   ▼
Gemini Model 1
   │
   ├── Success ─────────────► Response
   │
   └── Failure
         │
         ▼
   Gemini Model 2
         │
         ├── Success ───────► Response
         │
         └── Failure
               │
               ▼
          Gemini Model 3
               │
              ...
               │
               ▼
          Next provider
```

The configured Gemini cascade currently uses:

``` text
gemini-3.8-flash
        ↓
gemini-3.7-flash
        ↓
gemini-3.6-flash
        ↓
gemini-3.5-flash
        ↓
gemini-3.5-flash-lite
        ↓
gemini-3.1-flash-lite
```

The exact available models can change independently of the application
architecture because the list is configuration-driven.

------------------------------------------------------------------------

# 🗄️ Database Design

SchoolBuddy currently uses two primary entities.

## Conversation

``` text
Conversation
├── id
├── grade
├── subject
├── title
├── createdAt
└── updatedAt
```

## Message

``` text
Message
├── id
├── role
├── content
├── createdAt
└── conversation_id
```

Relationship:

``` text
Conversation 1 ──────────── N Message
```

A conversation can contain multiple user and assistant messages.

The message role is represented using:

``` text
USER
ASSISTANT
```

### Image storage decision

The actual Base64 image data is **not stored in MySQL**.

The database stores a lightweight reference such as:

``` text
[Image attached: question.png]
```

The image itself is processed as part of the request sent to the
multimodal provider.

------------------------------------------------------------------------

# 🔌 API Overview

## Send a chat request

``` http
POST /api/chat
Content-Type: application/json
```

The request can contain:

``` json
{
  "conversationId": 1,
  "grade": 5,
  "subject": "Science",
  "mode": "SOLVE",
  "message": "Solve this problem.",
  "imageBase64": "...",
  "imageMimeType": "image/png",
  "imageName": "question.png"
}
```

The response contains the conversation identifier and generated answer.

## Load a conversation

``` http
GET /api/conversations/{id}
```

This retrieves the stored conversation messages.

------------------------------------------------------------------------

# 🔐 Security and Safety Decisions

SchoolBuddy follows several practical MVP security decisions:

### Secrets

API keys and database credentials are supplied through environment
variables rather than committed to source control.

Examples:

``` text
GEMINI_API_KEY
SARVAM_API_KEY
OPENAI_API_KEY
OPENROUTER_API_KEY
MYSQL_JDBC_URL
MYSQLUSER
MYSQLPASSWORD
```

### CORS

The backend uses a configured frontend origin rather than relying on
unrestricted cross-origin access.

### Image handling

Only supported image MIME types are accepted by the frontend:

``` text
image/jpeg
image/png
image/webp
```

### Database privacy

Raw Base64 image data is not persisted in the conversation database.

### AI safety

The system prompt instructs the model to:

-   Avoid hallucinating image contents
-   State uncertainty when information cannot be determined
-   Keep explanations age appropriate
-   Encourage learning rather than answer dumping
-   Provide hints without immediately exposing answers
-   Avoid inappropriate content for school-age learners

> SchoolBuddy is an educational assistant and can make mistakes.
> Important information should be independently verified.

------------------------------------------------------------------------

# 🚀 Local Development

## Prerequisites

-   Java 21
-   Node.js
-   npm
-   MySQL
-   Git

## Project structure

``` text
D:\SchoolBuddy
│
├── schoolbuddy-backend
│   ├── src
│   ├── build.gradle
│   └── gradlew.bat
│
├── schoolbuddy-frontend
│   ├── app
│   ├── package.json
│   └── next.config.*
│
└── docs
```

------------------------------------------------------------------------

## Backend

``` powershell
cd D:\SchoolBuddy\schoolbuddy-backend
```

Configure the required environment variables in the local shell.

Example:

``` powershell
$env:MYSQLUSER="root"
$env:MYSQL_JDBC_URL="jdbc:mysql://localhost:3306/schoolbuddy"
$env:MYSQLPASSWORD="YOUR_LOCAL_PASSWORD"
$env:GEMINI_API_KEY="YOUR_GEMINI_KEY"
$env:SARVAM_API_KEY="YOUR_SARVAM_KEY"
```

Never commit real credentials.

Start Spring Boot:

``` powershell
.\gradlew.bat bootRun
```

Backend:

``` text
http://localhost:8080
```

------------------------------------------------------------------------

## Frontend

``` powershell
cd D:\SchoolBuddy\schoolbuddy-frontend
npm install
npm run dev
```

Frontend:

``` text
http://localhost:3000
```

Configure:

``` text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

for local development.

------------------------------------------------------------------------

# ☁️ Production Deployment

### Frontend

The Next.js application is deployed on **Vercel**.

Production frontend:

https://schoolbuddy-frontend.vercel.app/

### Backend

The Spring Boot application is deployed on **Render**.

Production backend:

https://schoolbuddy-backend-6on8.onrender.com/

### Database

Production MySQL is hosted on **Aiven**.

The production JDBC connection is supplied through environment
configuration and uses SSL.

------------------------------------------------------------------------

# 🧪 Evaluation and Testing

The application was regression-tested across:

### Grade levels

-   Grade 1
-   Grade 5
-   Grade 10

### Learning modes

-   Explain
-   Solve
-   Hint
-   Simplify
-   Practice
-   Quiz

### Additional tests

-   Fraction arithmetic
-   Mathematical LaTeX rendering
-   Multi-turn conversation context
-   Image-based question solving
-   Grade-aware explanations
-   Gemini model fallback behavior

A production image test successfully demonstrated:

``` text
Image
  ↓
24 apples / 6 children
  ↓
24 ÷ 6 = 4
  ↓
Each child gets 4 apples
```

The final regression suite confirmed the core MVP workflows across all
three target grade levels.

------------------------------------------------------------------------

# 📈 Engineering Highlights

This project demonstrates practical experience with:

-   Full-stack application architecture
-   REST API design
-   Spring Boot service layering
-   JPA/Hibernate persistence
-   Relational database modeling
-   Multimodal LLM integration
-   Prompt engineering
-   LLM provider abstraction
-   Multi-model fallback routing
-   Conversation-aware AI
-   Image-to-LLM pipelines
-   Mathematical response normalization
-   Frontend Markdown/LaTeX rendering
-   Environment-based configuration
-   Cloud deployment
-   Production debugging and regression testing

------------------------------------------------------------------------

# ⚠️ Current Scope and Limitations

SchoolBuddy is intentionally an MVP-focused educational tutor.

It does **not** currently include:

-   Custom LLM training
-   Fine-tuning
-   RAG/vector database
-   Voice interaction
-   Mobile applications
-   Teacher/parent dashboards
-   Payment systems
-   Social login
-   User account management
-   Complex analytics dashboards

These were intentionally kept outside the MVP scope so the core
multimodal tutoring workflow could be implemented, tested, and deployed
reliably.

------------------------------------------------------------------------

# 🔮 Future Improvements

Potential future enhancements include:

-   User authentication and profiles
-   Teacher/parent dashboards
-   Persistent user-level learning analytics
-   Retrieval-augmented generation for curriculum-specific material
-   Structured assessment tracking
-   More advanced evaluation pipelines
-   Streaming responses
-   Better image preprocessing
-   Provider health monitoring
-   Rate limiting and abuse protection
-   Automated AI response evaluation

------------------------------------------------------------------------

# 👨‍💻 Project

**SchoolBuddy.ai**

A practical full-stack demonstration of how a modern educational
application can combine:

``` text
React / Next.js
       +
Spring Boot
       +
MySQL
       +
Multimodal LLMs
       +
Prompt Engineering
       +
Fallback Architecture
       +
Cloud Deployment
```

### Links

-   🌐 Live: https://schoolbuddy-frontend.vercel.app/
-   💻 Frontend: https://github.com/MallickSayan407/schoolbuddy-frontend
-   ⚙️ Backend: https://github.com/MallickSayan407/schoolbuddy-backend
