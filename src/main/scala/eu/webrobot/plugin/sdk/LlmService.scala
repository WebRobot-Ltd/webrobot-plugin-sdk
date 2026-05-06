package eu.webrobot.plugin.sdk

/**
 * LLM inference via the platform's configured providers (Groq, OpenAI, Anthropic, …).
 *
 * Available to context-aware stages via [[WebroStageContext.llm]].
 *
 * Provider routing is handled by the platform: the org's configured default provider is used
 * when [[infer(prompt:String)*]] is called without a `provider` argument; pass an explicit
 * provider alias (e.g. `"groq"`, `"openai"`, `"anthropic"`) to route to a specific one.
 *
 * Plugins should never embed provider API keys — credential resolution is centralized
 * by the platform.
 *
 * @since 0.2.1
 */
trait LlmService extends Serializable {

  /** Run inference with just a user prompt and the org's default provider. */
  def infer(prompt: String): String

  /** Run inference with a specific provider alias (e.g. "groq", "openai", "anthropic"). */
  def infer(prompt: String, provider: String): String

  /** Run inference with a system prompt + user prompt + provider + optional model override. */
  def infer(prompt: String, systemPrompt: String, provider: String, model: String): String

  /** True when at least one LLM provider is configured for the org. */
  def isAvailable: Boolean
}
