# CodeCompass - AI-Powered Code Intelligence for IntelliJ

CodeCompass is an IntelliJ plugin that uses AI to help developers understand, navigate, and explore codebases more effectively. By integrating Retrieval-Augmented Generation (RAG) directly into your development environment, CodeCompass provides semantic understanding of your code through search, chat, and question-answering capabilities.

![CodeCompass Screenshot](https://i.imgur.com/8vzovAH.jpeg)

## Features

### 1. Semantic Code Search
Find relevant files based on natural language queries rather than just keywords:
- Search for concepts like "user authentication" or "payment processing"
- Results are ranked by semantic relevance, not just text matching
- Filter results by language or file type

### 2. Interactive Chat Interface
Have conversations about your codebase with context from previous questions:
- Ask follow-up questions naturally
- Chat maintains context between questions
- Reference specific files and code structures

### 3. Code Question Answering
Get detailed explanations about specific code functionality:
- Ask "How does the payment system work?"
- Get explanations that synthesize information from multiple files
- See relevant code snippets in context

## Getting Started

### Prerequisites
- IntelliJ IDEA (Community or Ultimate)
- Java 17 or higher
- [Qdrant vector database](https://qdrant.tech/) running locally (for vector storage)

### Installing Qdrant
Qdrant is required for storing and retrieving vector embeddings. You can install it using Docker:

```bash
docker pull qdrant/qdrant
docker run -p 6333:6333 -p 6334:6334 -v $(pwd)/qdrant_data:/qdrant/storage qdrant/qdrant
```

Verify Qdrant is running by visiting `http://localhost:6333/dashboard` in your browser.

### Installing CodeCompass Plugin
1. Download the latest release from the [GitHub Releases page](https://github.com/balakumardev/CodeCompass/releases)
2. In IntelliJ IDEA, go to Settings → Plugins → ⚙️ → Install Plugin from Disk...
3. Select the downloaded `.zip` file
4. Restart IntelliJ when prompted

## Setting Up AI Providers

CodeCompass supports three AI providers. You'll need to configure at least one:

### 1. OpenRouter Setup

1. Create an account at [OpenRouter](https://openrouter.ai/)
2. Generate an API key from your dashboard
3. In IntelliJ, go to Settings → Tools → CodeCompass
4. Select "OPENROUTER" as the AI Provider
5. Enter your API key in the "OpenRouter API Key" field
6. Configure models (or use defaults):
    - Embedding Model: `openai/text-embedding-3-small`
    - Generation Model: `google/gemini-2.0-flash-exp:free` or another model of your choice

### 2. Google Gemini Setup

1. Create a Google Cloud account if you don't have one
2. Visit the [Google AI Studio](https://makersuite.google.com/app/apikey)
3. Create an API key
4. In IntelliJ, go to Settings → Tools → CodeCompass
5. Select "GEMINI" as the AI Provider
6. Enter your API key in the "Gemini API Key" field
7. Configure models (or use defaults):
    - Embedding Model: `embedding-001`
    - Generation Model: `gemini-1.5-pro`

### 3. Ollama Setup (Local Models)

1. Install [Ollama](https://ollama.ai/) on your machine
2. Pull required models:
   ```bash
   ollama pull nomic-embed-text
   ollama pull codellama:7b-code
   ```
3. Start the Ollama service
4. In IntelliJ, go to Settings → Tools → CodeCompass
5. Select "OLLAMA" as the AI Provider
6. Verify the Ollama Endpoint is set to `http://localhost:11434`
7. Configure models (or use defaults):
    - Embedding Model: `nomic-embed-text`
    - Generation Model: `codellama:7b-code`

## Indexing Your Project

After setting up an AI provider and ensuring Qdrant is running:

1. Open your project in IntelliJ
2. Go to Tools → Search with CodeCompass
3. Click the "Reindex" button in the dialog
4. Wait for indexing to complete (this may take several minutes for large projects)
5. The status bar will show "Indexing completed" when finished

## Usage

### Semantic Search
1. Tools → Search with CodeCompass
2. Enter a natural language query (e.g., "how is user authentication implemented")
3. Browse results and click to open files
4. View the AI-generated context explaining the search results

### Question Answering
1. Tools → Ask CodeCompass
2. Enter your question about the codebase
3. View the AI-generated answer with relevant file references
4. Double-click on any file in the "Relevant Files" panel to open it

### Chat Interface
1. Open the CodeCompass Chat tool window (right sidebar)
2. Start asking questions about your code
3. Continue the conversation with follow-up questions
4. Use the "Show" button to expand the relevant files panel

## Troubleshooting

### Qdrant Connection Issues
- Ensure Qdrant is running on port 6333
- Check if you can access `http://localhost:6333/dashboard` in your browser
- Restart the Qdrant container if needed

### AI Service Connection Issues
- Verify your API key is correct
- Check your internet connection for cloud providers
- For Ollama, ensure the service is running locally

### Indexing Problems
- Make sure your AI provider is properly configured
- Check that Qdrant is running
- For large projects, increase memory allocation to IntelliJ

### Performance Considerations
- Local models (Ollama) require sufficient RAM and CPU
- Cloud providers have usage limits and may incur costs
- Indexing large projects may take significant time

## Privacy and Security

- When using Ollama, all code and queries stay on your local machine
- Cloud providers (OpenRouter, Gemini) require sending code snippets to their APIs
- No user data is collected by the plugin itself
- API keys are stored securely in IntelliJ's credential storage

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

For more information, visit the [GitHub repository](https://github.com/balakumardev/CodeCompass) or read the [introductory blog post](https://balakumar.dev/codecompass).

