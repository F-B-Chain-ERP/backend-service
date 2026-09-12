#!/usr/bin/env python3
# ==============================================================================
# SCRIPT SEED DỮ LIỆU LỚN CHO ERP-UTT (POSTGRESQL DIRECT BULK INSERTER)
# Mục đích: Nạp 100.000+ bản ghi phân hệ SYS, MENU, INV, PROC, POS để test tải & DB
# ==============================================================================

import json
import os
import random
import sys
import time
import uuid
from datetime import datetime, timedelta

import bcrypt
import psycopg2
from psycopg2.extras import execute_values

from config import (
    BATCH_SIZE,
    DB_HOST,
    DB_NAME,
    DB_PASSWORD,
    DB_PORT,
    DB_USER,
    DEFAULT_PASSWORD,
    SCALE_CONFIG,
)


def get_db_connection():
    print(f"Connecting to PostgreSQL at {DB_HOST}:{DB_PORT}/{DB_NAME} as {DB_USER}...")
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD,
        )
        conn.autocommit = False
        return conn
    except Exception as e:
        print(f"\n[ERROR] Không thể kết nối tới Database: {e}")
        print("Vui lòng kiểm tra biến môi trường DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD.")
        sys.exit(1)


def generate_bcrypt_hash(password: str) -> str:
    print("▶ 1. Mã hóa trước mật khẩu test bằng BCrypt (cost=12)...")
    salt = bcrypt.gensalt(rounds=12)
    hashed = bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")
    print(f"   Mật khẩu chung cho các tài khoản test: '{password}'")
    return hashed


def seed_branches(cursor, count: int):
    print(f"▶ 2. Đang tạo {count} chi nhánh (branch)...")
    branches = []
    now = datetime.now()
    branch_ids = []

    # Kiểm tra branch có sẵn
    cursor.execute("SELECT id, code FROM branch;")
    existing = cursor.fetchall()
    existing_codes = {r[1] for r in existing}
    branch_ids.extend([r[0] for r in existing])

    cities = ["Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Cần Thơ", "Hải Phòng", "Bình Dương", "Thái Nguyên"]

    for i in range(1, count + 1):
        code = f"BR_{i:03d}"
        if code in existing_codes:
            continue
        b_id = str(uuid.uuid4())
        city = random.choice(cities)
        name = f"Pine Drink {city} #{i}"
        address = f"Số {random.randint(1, 200)} Đường Phố {i}, {city}"
        phone = f"09{random.randint(10000000, 99999999)}"
        email = f"branch_{i:03d}@pinedrink.vn"
        lat = round(21.0 + random.uniform(-0.5, 0.5), 6)
        lng = round(105.8 + random.uniform(-0.5, 0.5), 6)
        branches.append((
            b_id, code, name, address, phone, email, lat, lng,
            "Asia/Ho_Chi_Minh", True, True, 15, "ACTIVE", now, now, "seeder", "seeder"
        ))
        branch_ids.append(b_id)

    if branches:
        query = """
        INSERT INTO branch (
            id, code, name, address, phone, email, latitude, longitude,
            timezone, supports_pickup, supports_delivery, average_preparation_minutes,
            status, created_at, updated_at, created_by, updated_by
        ) VALUES %s
        ON CONFLICT (code) DO NOTHING;
        """
        execute_values(cursor, query, branches)
        print(f"   Đã thêm mới {len(branches)} chi nhánh.")
    else:
        print("   Đã có đủ chi nhánh.")

    return branch_ids


def seed_scopes(cursor, branch_ids):
    print("▶ 3. Đang tạo scopes (ALL_SYSTEM, STORE, WAREHOUSE)...")
    now = datetime.now()
    scopes = []

    # Check ALL_SYSTEM
    cursor.execute("SELECT id FROM scope WHERE scope_type = 'ALL_SYSTEM';")
    row = cursor.fetchone()
    if not row:
        all_sys_id = str(uuid.uuid4())
        scopes.append((all_sys_id, "ALL_SYSTEM", None, "ACTIVE", now, now, "seeder", "seeder"))
    else:
        all_sys_id = row[0]

    for b_id in branch_ids:
        # Check store scope
        cursor.execute("SELECT id FROM scope WHERE scope_type = 'STORE' AND branch_id = %s;", (b_id,))
        if not cursor.fetchone():
            scopes.append((str(uuid.uuid4()), "STORE", b_id, "ACTIVE", now, now, "seeder", "seeder"))
        # Check warehouse scope
        cursor.execute("SELECT id FROM scope WHERE scope_type = 'WAREHOUSE' AND branch_id = %s;", (b_id,))
        if not cursor.fetchone():
            scopes.append((str(uuid.uuid4()), "WAREHOUSE", b_id, "ACTIVE", now, now, "seeder", "seeder"))

    if scopes:
        query = """
        INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
        VALUES %s ON CONFLICT DO NOTHING;
        """
        execute_values(cursor, query, scopes)
        print(f"   Đã thêm {len(scopes)} scopes.")

    cursor.execute("SELECT id, scope_type, branch_id FROM scope WHERE status = 'ACTIVE';")
    return cursor.fetchall()


def seed_roles(cursor):
    print("▶ 4. Kiểm tra các vai trò (roles)...")
    now = datetime.now()
    standard_roles = [
        ("ROLE_ADMIN", "Quản trị viên tối cao", "SYSTEM"),
        ("ROLE_MANAGER", "Quản lý chi nhánh", "CUSTOM"),
        ("ROLE_CASHIER", "Thu ngân POS", "CUSTOM"),
        ("ROLE_WAREHOUSE", "Thủ kho", "CUSTOM"),
        ("ROLE_BARISTA", "Nhân viên pha chế", "CUSTOM"),
    ]
    for code, name, r_type in standard_roles:
        cursor.execute("SELECT id FROM role WHERE code = %s;", (code,))
        if not cursor.fetchone():
            cursor.execute("""
            INSERT INTO role (id, code, name, type, status, created_at, updated_at, created_by, updated_by)
            VALUES (%s, %s, %s, %s, 'ACTIVE', %s, %s, 'seeder', 'seeder');
            """, (str(uuid.uuid4()), code, name, r_type, now, now))

    cursor.execute("SELECT id, code FROM role WHERE status = 'ACTIVE';")
    return {code: r_id for r_id, code in cursor.fetchall()}


def seed_accounts(cursor, count: int, branch_ids, role_map, scopes, hashed_password):
    print(f"▶ 5. Đang tạo {count} tài khoản người dùng (account & role assignment)...")
    now = datetime.now()
    accounts = []
    assignments = []
    test_user_credentials = []

    # Map scopes
    all_sys_scope = next((s[0] for s in scopes if s[1] == "ALL_SYSTEM"), None)
    store_scopes = {s[2]: s[0] for s in scopes if s[1] == "STORE"}

    cursor.execute("SELECT username FROM account;")
    existing_users = {r[0] for r in cursor.fetchall()}

    for i in range(1, count + 1):
        username = f"test_user_{i:04d}"
        if username in existing_users:
            continue
        acc_id = str(uuid.uuid4())
        branch_id = random.choice(branch_ids) if branch_ids else None
        email = f"user_{i:04d}@test.erp.vn"
        full_name = f"Nhân viên Test {i}"
        phone = f"08{random.randint(10000000, 99999999)}"

        accounts.append((
            acc_id, username, hashed_password, full_name, email, phone, None,
            "ACTIVE", now, "LOCAL", None, True, branch_id, 0, None, False,
            now, now, "seeder", "seeder"
        ))

        # Phân vai trò: 5% Admin, 20% Manager, 50% Cashier, 25% Barista
        rand_val = random.random()
        if rand_val < 0.05:
            role_code = "ROLE_ADMIN"
            scope_id = all_sys_scope
        elif rand_val < 0.25:
            role_code = "ROLE_MANAGER"
            scope_id = store_scopes.get(branch_id, all_sys_scope)
        elif rand_val < 0.75:
            role_code = "ROLE_CASHIER"
            scope_id = store_scopes.get(branch_id, all_sys_scope)
        else:
            role_code = "ROLE_BARISTA"
            scope_id = store_scopes.get(branch_id, all_sys_scope)

        role_id = role_map.get(role_code)
        if role_id and scope_id:
            assign_id = str(uuid.uuid4())
            assignments.append((
                assign_id, acc_id, role_id, scope_id, "ACTIVE", now, None,
                "seeder", now, "seeder", now, "seeder"
            ))

        test_user_credentials.append({
            "username": username,
            "email": email,
            "password": DEFAULT_PASSWORD,
            "role": role_code,
            "branch_id": str(branch_id) if branch_id else None
        })

    if accounts:
        query_acc = """
        INSERT INTO account (
            id, username, password, full_name, email, phone, avatar_url,
            status, last_login_at, auth_provider, provider_id, has_local_password,
            primary_branch_id, failed_login_attempts, locked_until, system_protected,
            created_at, updated_at, created_by, updated_by
        ) VALUES %s ON CONFLICT (username) DO NOTHING;
        """
        for i in range(0, len(accounts), BATCH_SIZE):
            execute_values(cursor, query_acc, accounts[i:i + BATCH_SIZE])

        query_assign = """
        INSERT INTO account_role_assignment (
            id, account_id, role_id, scope_id, status, assigned_at, expires_at,
            assigned_by, created_at, created_by, updated_at, updated_by
        ) VALUES %s ON CONFLICT DO NOTHING;
        """
        for i in range(0, len(assignments), BATCH_SIZE):
            execute_values(cursor, query_assign, assignments[i:i + BATCH_SIZE])

        print(f"   Đã thêm {len(accounts)} accounts và {len(assignments)} phân quyền.")

    # Xuất file json pool user để k6 dùng load test
    pool_path = os.path.join(os.path.dirname(__file__), "..", "k6", "users_pool.json")
    os.makedirs(os.path.dirname(pool_path), exist_ok=True)
    with open(pool_path, "w", encoding="utf-8") as f:
        json.dump(test_user_credentials[:1000], f, indent=2, ensure_ascii=False)
    print(f"   Đã xuất {len(test_user_credentials[:1000])} thông tin user test ra file '{pool_path}'.")

    cursor.execute("SELECT id FROM account;")
    return [r[0] for r in cursor.fetchall()]


def seed_catalog(cursor, cat_count: int, prod_count: int, mat_count: int):
    print("▶ 6. Đang tạo Unit, Categories, Materials, Products và Variants...")
    now = datetime.now()

    # 1. Units
    units_data = [
        ("KG", "Kilogram", "WEIGHT"),
        ("G", "Gram", "WEIGHT"),
        ("L", "Lít", "VOLUME"),
        ("ML", "Mililít", "VOLUME"),
        ("CAN", "Can", "VOLUME"),
        ("HOP", "Hộp", "COUNT"),
        ("CHAI", "Chai", "COUNT"),
        ("GOI", "Gói", "COUNT"),
        ("THUNG", "Thùng", "COUNT"),
        ("LY", "Ly", "COUNT"),
    ]
    for code, name, u_type in units_data:
        cursor.execute("SELECT id FROM unit WHERE code = %s;", (code,))
        if not cursor.fetchone():
            cursor.execute("""
            INSERT INTO unit (id, code, name, unit_type, status, created_at, updated_at, created_by, updated_by)
            VALUES (%s, %s, %s, %s, 'ACTIVE', %s, %s, 'seeder', 'seeder');
            """, (str(uuid.uuid4()), code, name, u_type, now, now))

    cursor.execute("SELECT id, code FROM unit;")
    unit_map = {code: u_id for u_id, code in cursor.fetchall()}
    base_weight_id = unit_map.get("G", list(unit_map.values())[0])

    # 2. Categories
    cursor.execute("SELECT id, category_type, code FROM category;")
    existing_cats = {(r[1], r[2]): r[0] for r in cursor.fetchall()}

    prod_cats = []
    mat_cats = []

    # Product categories
    for i in range(1, cat_count + 1):
        code = f"CAT_PROD_{i:02d}"
        if ("PRODUCT", code) not in existing_cats:
            c_id = str(uuid.uuid4())
            name = f"Danh mục Đồ uống #{i}"
            prod_cats.append((c_id, "PRODUCT", code, name, f"Mô tả danh mục {i}", None, i, "ACTIVE", now, now, "seeder", "seeder"))
            existing_cats[("PRODUCT", code)] = c_id

    # Material categories
    for i in range(1, 20 + 1):
        code = f"CAT_MAT_{i:02d}"
        if ("MATERIAL", code) not in existing_cats:
            c_id = str(uuid.uuid4())
            name = f"Nguyên vật liệu nhóm #{i}"
            mat_cats.append((c_id, "MATERIAL", code, name, f"Mô tả nguyên liệu {i}", None, i, "ACTIVE", now, now, "seeder", "seeder"))
            existing_cats[("MATERIAL", code)] = c_id

    all_new_cats = prod_cats + mat_cats
    if all_new_cats:
        cat_query = """
        INSERT INTO category (id, category_type, code, name, description, image_url, display_order, status, created_at, updated_at, created_by, updated_by)
        VALUES %s ON CONFLICT DO NOTHING;
        """
        execute_values(cursor, cat_query, all_new_cats)

    prod_cat_ids = [c_id for (ctype, _), c_id in existing_cats.items() if ctype == "PRODUCT"]
    mat_cat_ids = [c_id for (ctype, _), c_id in existing_cats.items() if ctype == "MATERIAL"]

    # 3. Materials
    cursor.execute("SELECT id, code FROM material;")
    existing_mats = {r[1]: r[0] for r in cursor.fetchall()}
    materials = []
    mat_names = ["Hạt Cà phê Robusta", "Hạt Cà phê Arabica", "Sữa đặc Ông Thọ", "Sữa tươi thanh trùng",
                 "Đường kính trắng", "Syrup Đào", "Syrup Vải", "Bột Matcha Nhật Bản", "Trà ô long",
                 "Trà lài", "Trân châu đen", "Trân châu hoàng kim", "Thạch đào", "Kem béo thực vật"]

    for i in range(1, mat_count + 1):
        code = f"MAT_{i:04d}"
        if code in existing_mats:
            continue
        m_id = str(uuid.uuid4())
        name = f"{random.choice(mat_names)} Loại {i}"
        cat_id = random.choice(mat_cat_ids)
        materials.append((
            m_id, cat_id, code, name, base_weight_id, 50.0, 180, False,
            "ACTIVE", now, now, "seeder", "seeder"
        ))
        existing_mats[code] = m_id

    if materials:
        mat_query = """
        INSERT INTO material (
            id, category_id, code, name, base_unit_id, min_stock_alert,
            shelf_life_days, is_perishable, status, created_at, updated_at, created_by, updated_by
        ) VALUES %s ON CONFLICT (code) DO NOTHING;
        """
        for i in range(0, len(materials), BATCH_SIZE):
            execute_values(cursor, mat_query, materials[i:i + BATCH_SIZE])
        print(f"   Đã thêm {len(materials)} materials.")

    material_ids = list(existing_mats.values())

    # 4. Products & Variants
    cursor.execute("SELECT id, code FROM product;")
    existing_prods = {r[1]: r[0] for r in cursor.fetchall()}
    products = []
    variants = []
    prod_names = ["Trà sữa Oolong Nướng", "Cà phê Muối Hoàng Gia", "Trà Đào Cam Sả", "Matcha Latte Đá Xay",
                  "Cà phê Phin Sữa Đá", "Trà Hoa Cúc Mật Ong", "Sinh tố Xoài Cốt Dừa", "Bạc xỉu 3 tầng",
                  "Americano Đá", "Latte Nóng", "Trà Vải Hoa Nhài", "Trà Đen Macchiato"]

    for i in range(1, prod_count + 1):
        p_code = f"PRD_{i:04d}"
        if p_code in existing_prods:
            continue
        p_id = str(uuid.uuid4())
        name = f"{random.choice(prod_names)} #{i}"
        cat_id = random.choice(prod_cat_ids)
        base_price = float(random.randint(25, 65) * 1000)

        products.append((
            p_id, cat_id, p_code, name, f"Mô tả cho {name}", None, base_price,
            10, (i % 5 == 0), (i % 3 == 0), False, "0,30,50,70,100", "0,30,50,70,100",
            "ACTIVE", now, now, "seeder", "seeder"
        ))
        existing_prods[p_code] = p_id

        # Tạo 3 variants: Size S (+0), Size M (+5,000), Size L (+10,000)
        sizes = [("S", "Size Nhỏ", 0.0), ("M", "Size Vừa", 5000.0), ("L", "Size Lớn", 10000.0)]
        for s_code, s_name, delta in sizes:
            v_id = str(uuid.uuid4())
            variants.append((
                v_id, p_id, f"{p_code}_{s_code}", f"{name} ({s_name})", s_code,
                delta, 0, "ACTIVE", now, now, "seeder", "seeder"
            ))

    if products:
        prod_query = """
        INSERT INTO product (
            id, category_id, code, name, description, image_url, base_price,
            preparation_minutes, is_featured, is_best_seller, is_combo,
            available_ice_levels, available_sugar_levels, status, created_at, updated_at, created_by, updated_by
        ) VALUES %s ON CONFLICT (code) DO NOTHING;
        """
        for i in range(0, len(products), BATCH_SIZE):
            execute_values(cursor, prod_query, products[i:i + BATCH_SIZE])

        var_query = """
        INSERT INTO product_variant (
            id, product_id, variant_code, variant_name, size_label, price_delta,
            display_order, status, created_at, updated_at, created_by, updated_by
        ) VALUES %s ON CONFLICT (product_id, variant_code) DO NOTHING;
        """
        for i in range(0, len(variants), BATCH_SIZE):
            execute_values(cursor, var_query, variants[i:i + BATCH_SIZE])

        print(f"   Đã thêm {len(products)} products và {len(variants)} product_variants.")

    cursor.execute("SELECT id, product_id, variant_code FROM product_variant WHERE status = 'ACTIVE';")
    variant_records = cursor.fetchall()

    return material_ids, list(existing_prods.values()), variant_records, base_weight_id


def seed_warehouses_and_stock(cursor, branch_ids, material_ids, wh_count: int, target_stock_count: int):
    print(f"▶ 7. Đang tạo {wh_count} kho (warehouse) và {target_stock_count} số dư tồn kho (stock_balance)...")
    now = datetime.now()

    cursor.execute("SELECT id, code, branch_id FROM warehouse;")
    existing_wh = {r[1]: (r[0], r[2]) for r in cursor.fetchall()}
    warehouses = []

    for i in range(1, wh_count + 1):
        code = f"WH_{i:03d}"
        if code in existing_wh:
            continue
        wh_id = str(uuid.uuid4())
        branch_id = random.choice(branch_ids) if branch_ids else None
        name = f"Kho Chi Nhánh #{i}"
        warehouses.append((
            wh_id, branch_id, code, name, "Kho lưu trữ và phân phối nguyên liệu",
            f"Địa chỉ kho {i}", "ACTIVE", now, now, "seeder", "seeder"
        ))
        existing_wh[code] = (wh_id, branch_id)

    if warehouses:
        wh_query = """
        INSERT INTO warehouse (id, branch_id, code, name, description, address, status, created_at, updated_at, created_by, updated_by)
        VALUES %s ON CONFLICT (code) DO NOTHING;
        """
        execute_values(cursor, wh_query, warehouses)
        print(f"   Đã thêm {len(warehouses)} kho mới.")

    wh_list = [v[0] for v in existing_wh.values()]

    # Stock Balances
    cursor.execute("SELECT warehouse_id, material_id FROM stock_balance;")
    existing_balances = {(r[0], r[1]) for r in cursor.fetchall()}
    stock_balances = []

    created_count = 0
    for wh_id in wh_list:
        sample_mats = random.sample(material_ids, min(len(material_ids), target_stock_count // max(1, len(wh_list))))
        for m_id in sample_mats:
            if (wh_id, m_id) in existing_balances:
                continue
            sb_id = str(uuid.uuid4())
            qty = round(random.uniform(500.0, 50000.0), 3)
            stock_balances.append((
                sb_id, wh_id, m_id, qty, 0.0, 0.0, now, "ACTIVE", now, now, "seeder", "seeder"
            ))
            existing_balances.add((wh_id, m_id))
            created_count += 1

    if stock_balances:
        sb_query = """
        INSERT INTO stock_balance (
            id, warehouse_id, material_id, quantity, allocated_quantity,
            pending_in_quantity, last_counted_at, status, created_at, updated_at, created_by, updated_by
        ) VALUES %s ON CONFLICT (warehouse_id, material_id) DO NOTHING;
        """
        for i in range(0, len(stock_balances), BATCH_SIZE):
            execute_values(cursor, sb_query, stock_balances[i:i + BATCH_SIZE])
        print(f"   Đã nạp {len(stock_balances)} bản ghi tồn kho (stock_balance).")

    return wh_list


def seed_customers_and_orders(cursor, branch_ids, variants, cust_count: int, order_count: int):
    print(f"▶ 8. Đang tạo {cust_count} khách hàng và {order_count} đơn hàng (orders & items)...")
    now = datetime.now()

    # 1. Customers
    cursor.execute("SELECT id, customer_code FROM customer;")
    existing_custs = {r[1]: r[0] for r in cursor.fetchall()}
    customers = []

    for i in range(1, cust_count + 1):
        code = f"CUST_{i:06d}"
        if code in existing_custs:
            continue
        c_id = str(uuid.uuid4())
        name = f"Khách Hàng Thân Thiết {i}"
        phone = f"03{random.randint(10000000, 99999999)}"
        email = f"customer_{i:06d}@gmail.com"
        customers.append((
            c_id, code, f"cust_{i:06d}", name, phone, email, None, "LOCAL",
            None, False, True, None, None, "ACTIVE", now, now, "seeder", now, "seeder"
        ))
        existing_custs[code] = c_id

    if customers:
        c_query = """
        INSERT INTO customer (
            id, customer_code, username, full_name, phone, email, avatar_url,
            auth_provider, provider_id, has_local_password, email_verified,
            date_of_birth, gender, status, last_login_at, created_at, created_by, updated_at, updated_by
        ) VALUES %s ON CONFLICT (customer_code) DO NOTHING;
        """
        for i in range(0, len(customers), BATCH_SIZE):
            execute_values(cursor, c_query, customers[i:i + BATCH_SIZE])
        print(f"   Đã thêm {len(customers)} khách hàng.")

    cust_list = list(existing_custs.values())

    # 2. Orders & Order Items
    cursor.execute("SELECT COUNT(*) FROM orders;")
    current_orders = cursor.fetchone()[0]
    needed_orders = max(0, order_count - current_orders)

    if needed_orders > 0:
        print(f"   Đang sinh {needed_orders} đơn hàng mới...")
        orders = []
        order_items = []
        statuses = ["COMPLETED", "COMPLETED", "COMPLETED", "PREPARING", "PENDING", "CANCELLED"]
        pay_methods = ["CASH", "VNPAY", "MOMO", "BANK_TRANSFER"]

        # Timestamp rải đều trong 30 ngày qua
        start_date = now - timedelta(days=30)

        for i in range(1, needed_orders + 1):
            o_id = str(uuid.uuid4())
            order_code = f"ORD_{int(time.time())}_{i:06d}"
            b_id = random.choice(branch_ids)
            c_id = random.choice(cust_list) if (cust_list and random.random() < 0.7) else None
            cust_name = f"Khách Mua {i}"
            cust_phone = f"09{random.randint(10000000, 99999999)}"
            order_status = random.choice(statuses)
            pay_method = random.choice(pay_methods)
            pay_status = "PAID" if order_status == "COMPLETED" else "UNPAID"

            order_date = start_date + timedelta(seconds=random.randint(0, 30 * 86400))

            # Items trong đơn
            item_count = random.randint(1, 4)
            subtotal = 0.0
            order_variants = random.sample(variants, min(len(variants), item_count))

            for v in order_variants:
                item_id = str(uuid.uuid4())
                var_id, prod_id, _ = v
                qty = random.randint(1, 3)
                price = float(random.randint(30, 60) * 1000)
                total_p = qty * price
                subtotal += total_p
                order_items.append((
                    item_id, o_id, prod_id, var_id, qty, "NORMAL", "NORMAL",
                    price, total_p, 0.0, "ACTIVE", order_date, "seeder", order_date, "seeder"
                ))

            orders.append((
                o_id, order_code, b_id, c_id, cust_name, cust_phone, None,
                "PICKUP", order_status, pay_method, pay_status, subtotal, 0.0,
                0.0, subtotal, subtotal * 0.4, None, None, None,
                order_date, order_date, order_date, None, None,
                order_date if order_status == "COMPLETED" else None,
                order_date if order_status == "CANCELLED" else None,
                None, None, order_date, "seeder", order_date, "seeder"
            ))

            if len(orders) >= BATCH_SIZE:
                _insert_order_batch(cursor, orders, order_items)
                orders.clear()
                order_items.clear()
                print(f"   -> Đã ghi {i}/{needed_orders} orders...")

        if orders:
            _insert_order_batch(cursor, orders, order_items)
            print(f"   Đã hoàn tất nạp {needed_orders} orders.")
    else:
        print(f"   Hiện đã có {current_orders} orders trong database (đạt mục tiêu).")


def _insert_order_batch(cursor, orders, items):
    order_query = """
    INSERT INTO orders (
        id, order_code, branch_id, customer_id, customer_name, customer_phone, customer_email,
        order_type, status, payment_method, payment_status, subtotal_amount, discount_amount,
        delivery_fee, total_amount, total_cogs_amount, pickup_time, delivery_address, note,
        confirmed_at, prepared_at, ready_at, delivering_at, delivered_at, completed_at,
        cancelled_at, rejected_at, cancel_reason, created_at, created_by, updated_at, updated_by
    ) VALUES %s ON CONFLICT (order_code) DO NOTHING;
    """
    execute_values(cursor, order_query, orders)

    item_query = """
    INSERT INTO order_item (
        id, order_id, product_id, variant_id, quantity, sugar_level, ice_level,
        unit_price, total_price, cogs_amount, status, created_at, created_by, updated_at, updated_by
    ) VALUES %s ON CONFLICT DO NOTHING;
    """
    execute_values(cursor, item_query, items)


def seed_audit_logs(cursor, count: int, branch_ids, account_ids):
    print(f"▶ 9. Đang tạo {count} bản ghi audit_log...")
    now = datetime.now()
    cursor.execute("SELECT COUNT(*) FROM audit_log;")
    current = cursor.fetchone()[0]
    needed = max(0, count - current)

    if needed <= 0:
        print(f"   Hiện đã có {current} bản ghi audit_log (đạt chỉ tiêu).")
        return

    actions = ["CREATE_ORDER", "UPDATE_STOCK", "LOGIN_SUCCESS", "CHANGE_PRICE", "APPROVE_PO", "STOCK_OUT"]
    modules = ["POS", "INV", "AUTH", "MENU", "PROC", "INV"]

    audit_logs = []
    for i in range(1, needed + 1):
        idx = random.randint(0, len(actions) - 1)
        action = actions[idx]
        module = modules[idx]
        b_id = random.choice(branch_ids) if branch_ids else None
        acc_id = random.choice(account_ids) if account_ids else None
        log_time = now - timedelta(minutes=random.randint(1, 43200))

        audit_logs.append((
            str(uuid.uuid4()), "ACTIVE", "USER", acc_id, action, module,
            "RECORD", str(uuid.uuid4()), b_id, f"192.168.1.{random.randint(10, 250)}",
            "k6-load-tester/1.0", json.dumps({"status": "SUCCESS"}),
            json.dumps({"processed_at": str(log_time)}),
            log_time, "seeder", log_time, "seeder"
        ))

        if len(audit_logs) >= BATCH_SIZE:
            _insert_audit_batch(cursor, audit_logs)
            audit_logs.clear()

    if audit_logs:
        _insert_audit_batch(cursor, audit_logs)
    print(f"   Đã tạo xong {needed} audit logs.")


def _insert_audit_batch(cursor, logs):
    query = """
    INSERT INTO audit_log (
        id, status, actor_type, actor_id, action, module, target_type,
        target_id, branch_id, ip_address, user_agent, before_data, after_data,
        created_at, created_by, updated_at, updated_by
    ) VALUES %s ON CONFLICT DO NOTHING;
    """
    execute_values(cursor, query, logs)


def main():
    print("==============================================================================")
    print("      BẮT ĐẦU QUY TRÌNH SEED DỮ LIỆU LỚN CHO ERP-UTT (ENTERPRISE SCALE)")
    print("==============================================================================")
    start_time = time.time()

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        # 1. Mã hóa mật khẩu test
        hashed_pwd = generate_bcrypt_hash(DEFAULT_PASSWORD)

        # 2. Seed Branches
        branch_ids = seed_branches(cursor, SCALE_CONFIG["BRANCH_COUNT"])
        conn.commit()

        # 3. Seed Scopes
        scopes = seed_scopes(cursor, branch_ids)
        conn.commit()

        # 4. Seed Roles
        role_map = seed_roles(cursor)
        conn.commit()

        # 5. Seed Accounts & Role Assignments
        account_ids = seed_accounts(cursor, SCALE_CONFIG["ACCOUNT_COUNT"], branch_ids, role_map, scopes, hashed_pwd)
        conn.commit()

        # 6. Seed Catalog (Categories, Units, Materials, Products, Variants)
        material_ids, product_ids, variants, base_unit_id = seed_catalog(
            cursor, SCALE_CONFIG["CATEGORY_COUNT"], SCALE_CONFIG["PRODUCT_COUNT"], SCALE_CONFIG["MATERIAL_COUNT"]
        )
        conn.commit()

        # 7. Seed Warehouses & Stock Balances
        wh_ids = seed_warehouses_and_stock(
            cursor, branch_ids, material_ids, SCALE_CONFIG["WAREHOUSE_COUNT"], SCALE_CONFIG["STOCK_BALANCE_TARGET"]
        )
        conn.commit()

        # 8. Seed Customers & Orders
        seed_customers_and_orders(
            cursor, branch_ids, variants, SCALE_CONFIG["CUSTOMER_COUNT"], SCALE_CONFIG["ORDER_COUNT"]
        )
        conn.commit()

        # 9. Seed Audit Logs
        seed_audit_logs(cursor, SCALE_CONFIG["AUDIT_LOG_COUNT"], branch_ids, account_ids)
        conn.commit()

        print("\n▶ 10. Chạy PostgreSQL ANALYZE để cập nhật thống kê cho bộ lập lịch truy vấn...")
        cursor.execute("ANALYZE;")
        conn.commit()

        elapsed = time.time() - start_time
        print("\n==============================================================================")
        print(f"      HOÀN THÀNH SEED DỮ LIỆU THÀNH CÔNG TRONG {elapsed:.2f} GIÂY!")
        print("==============================================================================")

    except Exception as e:
        conn.rollback()
        print(f"\n[ERROR] Xảy ra lỗi trong quá trình seed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    main()
