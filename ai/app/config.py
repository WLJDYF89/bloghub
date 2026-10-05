from pydantic_settings import BaseSettings, SettingsConfigDict

from pathlib import Path

base = Path(__file__).parent.parent

class Settings(BaseSettings):
    """运行配置。全部来自 ai/.env，缺失的项走下面的默认值。"""

    model_config = SettingsConfigDict(
        env_file=f"{base}/.env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    llm_base_url: str = "https://api.deepseek.com/v1"
    llm_api_key: str = ""
    llm_model: str = "deepseek-flash"
    llm_temperature: float = 0.7
    # llm_max_tokens: int = 2048
    # 单次请求的读超时。首 token 可能要等几十秒，不要设太小。
    llm_timeout_read: float = 180.0

    server_port: int = 8000

    # 与 Spring 侧 bloghub.ai.shared-token 一致；二期工具回调时用于服务间身份
    shared_token: str = "dev-ai-token"

    @property
    def configured(self) -> bool:
        return bool(self.llm_api_key.strip())


settings = Settings()
