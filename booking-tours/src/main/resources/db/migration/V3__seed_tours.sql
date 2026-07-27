-- ----------------------------------------------------------------
-- Tours mẫu (10 tour — mix đủ category, status, avg_rating)
-- category_id tham chiếu theo name để tránh hardcode id.
-- INSERT IGNORE → an toàn khi chạy lại migration.
-- ----------------------------------------------------------------
INSERT IGNORE INTO tours (title, description, price, duration_days, max_participants,
                   departure_location, destination, departure_date,
                   thumbnail_url, category_id, status, avg_rating)
VALUES
    (
        'Hạ Long Bay 3N2Đ Luxury Cruise',
        'Khám phá kỳ quan thiên nhiên thế giới Vịnh Hạ Long trên du thuyền 5 sao. '
            'Lịch trình bao gồm chèo kayak hang Luồn, tham quan làng chài Cửa Vạn, '
            'trải nghiệm nấu ăn cùng đầu bếp và ngắm bình minh trên boong tàu.',
        6900000, 3, 20,
        'Hà Nội', 'Hạ Long, Quảng Ninh',
        '2026-07-15', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch biển'), 'ACTIVE', 4.8
    ),
    (
        'Đà Nẵng – Hội An 4N3Đ',
        'Hành trình trải nghiệm thành phố đáng sống Đà Nẵng và phố cổ Hội An di sản UNESCO. '
            'Tham quan Bà Nà Hills – Cầu Vàng, bãi biển Mỹ Khê, làng rau Trà Quế '
            'và thả đèn hoa đăng trên sông Hoài.',
        5500000, 4, 25,
        'Hồ Chí Minh', 'Đà Nẵng – Hội An, Quảng Nam',
        '2026-07-20', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch văn hóa'), 'ACTIVE', 4.6
    ),
    (
        'Sapa Trekking Fansipan 3N2Đ',
        'Chinh phục đỉnh Fansipan 3.143m – "nóc nhà Đông Dương" cùng hướng dẫn viên bản địa. '
            'Khám phá ruộng bậc thang Mù Cang Chải, bản làng H''Mông, Dao Đỏ '
            'và thưởng thức đặc sản thắng cố, mèn mén giữa núi rừng Tây Bắc.',
        4200000, 3, 15,
        'Hà Nội', 'Sapa, Lào Cai',
        '2026-08-01', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch núi'), 'ACTIVE', 4.7
    ),
    (
        'Phú Quốc 5N4Đ Resort & Snorkeling',
        'Kỳ nghỉ dưỡng sang trọng tại "đảo ngọc" Phú Quốc. Lưu trú resort 4 sao, '
            'lặn ngắm san hô tại Hòn Thơm, tham quan Vinpearl Safari, '
            'thưởng thức hải sản tươi sống tại chợ đêm Phú Quốc.',
        9800000, 5, 18,
        'Hồ Chí Minh', 'Phú Quốc, Kiên Giang',
        '2026-08-10', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch nghỉ dưỡng'), 'ACTIVE', 4.9
    ),
    (
        'Mù Cang Chải Mùa Vàng 2N1Đ',
        'Tour cuối tuần ngắm ruộng bậc thang vàng óng Mù Cang Chải vào mùa lúa chín tháng 9-10. '
            'Đạp xe giữa triền đồi, chụp ảnh tại La Pán Tẩn và Chế Cu Nha, '
            'nghỉ đêm homestay người Mông.',
        2800000, 2, 12,
        'Hà Nội', 'Mù Cang Chải, Yên Bái',
        '2026-09-20', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch núi'), 'ACTIVE', 4.5
    ),
    (
        'Côn Đảo Khám Phá 3N2Đ',
        'Hành trình về vùng đất thiêng Côn Đảo – nơi hội tụ lịch sử hào hùng và thiên nhiên hoang sơ. '
            'Lặn biển tại vườn quốc gia Côn Đảo, viếng nghĩa trang Hàng Dương, '
            'ngắm rùa biển đẻ trứng (theo mùa).',
        7200000, 3, 16,
        'Hồ Chí Minh', 'Côn Đảo, Bà Rịa – Vũng Tàu',
        '2026-08-25', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch biển'), 'ACTIVE', 4.7
    ),
    (
        'Hà Giang Loop 4N3Đ Motorbike',
        'Vòng cung Hà Giang – cung đường phượt huyền thoại qua Đồng Văn, Mèo Vạc, '
            'đèo Mã Pí Lèng. Trải nghiệm lái xe máy hoặc ngồi sau xe ôm bản địa, '
            'thăm cao nguyên đá Đồng Văn – Di sản địa chất toàn cầu UNESCO.',
        3800000, 4, 10,
        'Hà Nội', 'Hà Giang',
        '2026-09-05', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch mạo hiểm'), 'ACTIVE', 4.9
    ),
    (
        'Nha Trang Biển Xanh 4N3Đ',
        'Nghỉ dưỡng và vui chơi tại thành phố biển Nha Trang sôi động. '
            'Tour 4 đảo bằng tàu gỗ, lặn scuba diving tại Hòn Mun, '
            'tắm bùn khoáng Tháp Bà, khám phá Vinwonders Nha Trang.',
        5200000, 4, 30,
        'Hà Nội', 'Nha Trang, Khánh Hòa',
        '2026-07-28', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch biển'), 'ACTIVE', 4.4
    ),
    (
        'Huế Đế Đô Ngàn Năm 3N2Đ',
        'Hành trình tìm về cố đô Huế – kinh đô cuối cùng của Việt Nam. '
            'Tham quan Đại Nội, lăng Tự Đức, lăng Khải Định, chùa Thiên Mụ, '
            'thưởng thức ẩm thực cung đình và trải nghiệm áo dài Huế.',
        3600000, 3, 22,
        'Hà Nội', 'Huế, Thừa Thiên Huế',
        '2026-08-15', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch văn hóa'), 'ACTIVE', 4.3
    ),
    (
        'Phan Thiết Mũi Né Cát Bay 3N2Đ',
        'Trải nghiệm trượt cát trên đồi cát Mũi Né, lướt ván diều (kitesurfing) '
            'tại bãi biển Mũi Né nổi tiếng thế giới. Khám phá suối Tiên, làng chài Mũi Né '
            'và nghỉ dưỡng resort ven biển.',
        3900000, 3, 20,
        'Hồ Chí Minh', 'Phan Thiết – Mũi Né, Bình Thuận',
        '2026-09-12', NULL,
        (SELECT id FROM categories WHERE name = 'Du lịch mạo hiểm'), 'INACTIVE', 4.1
    );
