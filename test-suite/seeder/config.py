import os

# ==============================================================================
# CẤU HÌNH KẾT NỐI DATABASE VÀ THAM SỐ SEED DỮ LIỆU LỚN
# ==============================================================================

# Thông số kết nối PostgreSQL
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", "erp_dev")
DB_USER = os.getenv("DB_USERNAME", "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "postgres")

# Base URL của Backend Service (để xuất file test pool)
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# Mặc định mật khẩu chung cho toàn bộ tài khoản test để dễ mô phỏng k6:
DEFAULT_PASSWORD = os.getenv("TEST_USER_PASSWORD", "123456789")

# Đường dẫn tự động xuất users_pool.json sang thư mục k6
USERS_POOL_OUTPUT_PATH = os.getenv(
    "USERS_POOL_OUTPUT_PATH",
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "k6", "users_pool.json")
)

# Kích thước Batch Insert (dùng psycopg2.extras.execute_values)
BATCH_SIZE = int(os.getenv("SEED_BATCH_SIZE", "2000"))

# Quy mô số lượng dữ liệu cần nạp (Có thể điều chỉnh qua biến môi trường)
SCALE_CONFIG = {
    "BRANCH_COUNT": int(os.getenv("SEED_BRANCH_COUNT", "20")),
    "ACCOUNT_COUNT": int(os.getenv("SEED_ACCOUNT_COUNT", "500")),
    "CATEGORY_COUNT": int(os.getenv("SEED_CATEGORY_COUNT", "50")),
    "PRODUCT_COUNT": int(os.getenv("SEED_PRODUCT_COUNT", "1000")),
    "VARIANTS_PER_PRODUCT": 3,
    "UNIT_COUNT": 15,
    "MATERIAL_COUNT": int(os.getenv("SEED_MATERIAL_COUNT", "500")),
    "SUPPLIER_COUNT": int(os.getenv("SEED_SUPPLIER_COUNT", "100")),
    "WAREHOUSE_COUNT": int(os.getenv("SEED_WAREHOUSE_COUNT", "30")),
    "STOCK_BALANCE_TARGET": int(os.getenv("SEED_STOCK_BALANCE_TARGET", "20000")),
    "CUSTOMER_COUNT": int(os.getenv("SEED_CUSTOMER_COUNT", "5000")),
    "ORDER_COUNT": int(os.getenv("SEED_ORDER_COUNT", "50000")),
    "AUDIT_LOG_COUNT": int(os.getenv("SEED_AUDIT_LOG_COUNT", "50000")),
}
