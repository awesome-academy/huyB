-- ----------------------------------------------------------------
-- Reviews mẫu (10 bài — mix đủ type, status)
-- user_id tham chiếu users đã có trong hệ thống.
-- ON CONFLICT DO NOTHING → an toàn khi chạy lại migration.
-- ----------------------------------------------------------------
INSERT INTO reviews (user_id, type, title, content, status, likes_count, created_at, updated_at)
VALUES
    (
        2, 'PLACE',
        'Vịnh Hạ Long – Kỳ quan không thể bỏ lỡ',
        'Đây là lần thứ hai tôi đến Hạ Long nhưng cảm giác choáng ngợp vẫn không hề giảm. '
            'Du thuyền 5 sao phục vụ tuyệt vời, kayak qua hang Luồn lúc sáng sớm là trải nghiệm '
            'không thể quên. Nước biển xanh ngọc, không khí trong lành – hoàn toàn xứng đáng với '
            'danh hiệu Di sản Thiên nhiên Thế giới.',
        'PUBLISHED', 128, '2026-01-10 08:30:00', '2026-01-10 08:30:00'
    ),
    (
        2, 'FOOD',
        'Ẩm thực Hội An – Thiên đường của những tâm hồn mê ăn',
        'Cao lầu chuẩn vị chỉ có ở Hội An – sợi mì dai, thịt heo xá xíu, tóp mỡ giòn tan '
            'cùng rau sống tươi ngon. Cơm gà Phố Hội thơm lừng, bánh mì Phượng giòn rụm. '
            'Mỗi góc phố Hội An đều ẩn chứa một hương vị khó quên. Nhất định phải thử buổi tối '
            'tại chợ đêm với đủ món ăn vặt địa phương.',
        'PUBLISHED', 95, '2026-01-18 12:00:00', '2026-01-18 12:00:00'
    ),
    (
        3, 'PLACE',
        'Đèo Mã Pí Lèng – Cung đường tứ đại đỉnh đèo hùng vĩ',
        'Chinh phục Mã Pí Lèng trên cung đường Hà Giang loop là đỉnh cao của mọi hành trình '
            'phượt tôi từng trải qua. Vực sâu thăm thẳm, sông Nho Quế uốn lượn như dải lụa xanh '
            'bên dưới, mây trắng vờn quanh đỉnh núi. Nếu chỉ được chọn một điểm đến ở Việt Nam, '
            'tôi sẽ không ngần ngại chọn nơi này.',
        'PUBLISHED', 214, '2026-02-03 07:15:00', '2026-02-03 07:15:00'
    ),
    (
        4, 'NEWS',
        'Phú Quốc mở thêm 5 tuyến cáp treo mới – Du lịch đảo ngọc bùng nổ 2026',
        'Theo thông tin từ UBND tỉnh Kiên Giang, Phú Quốc sẽ khánh thành thêm 5 tuyến cáp treo '
            'mới trong năm 2026, kết nối các đảo nhỏ quanh Hòn Thơm. Lượng khách quốc tế đến '
            'Phú Quốc trong quý I/2026 tăng 38% so với cùng kỳ năm ngoái. Đây là tín hiệu tích cực '
            'cho ngành du lịch sau giai đoạn phục hồi hậu đại dịch.',
        'PUBLISHED', 67, '2026-02-14 09:00:00', '2026-02-14 09:00:00'
    ),
    (
        4, 'FOOD',
        'Bún bò Huế chuẩn gốc – Linh hồn ẩm thực cố đô',
        'Không giống bún bò ở Sài Gòn hay Hà Nội, bún bò Huế chuẩn gốc có nước dùng đậm đà '
            'vị sả, mắm ruốc, ớt sa tế đặc trưng, miếng chả cua viên tròn đầy. '
            'Một tô bún bò lúc 6 giờ sáng tại quán nhỏ trong hẻm phố Huế là cách tốt nhất '
            'để bắt đầu ngày khám phá kinh thành.',
        'PUBLISHED', 82, '2026-03-01 06:45:00', '2026-03-01 06:45:00'
    ),
    (
        3, 'PLACE',
        'Ruộng bậc thang Mù Cang Chải – Bức tranh thiên nhiên mùa vàng',
        'Tháng 9 về, Mù Cang Chải khoác lên mình tấm áo vàng rực rỡ. '
            'Những thửa ruộng bậc thang xếp tầng từ chân núi lên đỉnh đồi tạo nên khung cảnh '
            'ngoạn mục hiếm nơi nào sánh được. Đạp xe buổi sáng sớm, hít thở không khí mát lành '
            'và chụp những bức ảnh để đời là trải nghiệm tôi sẽ mãi trân trọng.',
        'PUBLISHED', 176, '2026-03-20 10:30:00', '2026-03-20 10:30:00'
    ),
    (
        2, 'NEWS',
        'Top 5 xu hướng du lịch Việt Nam nửa cuối 2026',
        'Các chuyên gia du lịch dự báo nửa cuối 2026 sẽ chứng kiến sự bùng nổ của du lịch '
            'chậm (slow travel), du lịch sinh thái và các tour trải nghiệm văn hóa bản địa. '
            'Hà Giang, Cao Bằng và vùng Tây Bắc được kỳ vọng trở thành điểm đến hot nhất, '
            'đặc biệt với du khách quốc tế muốn tìm hiểu văn hóa các dân tộc thiểu số.',
        'PUBLISHED', 43, '2026-04-05 11:00:00', '2026-04-05 11:00:00'
    ),
    (
        4, 'PLACE',
        'Côn Đảo – Nơi lịch sử và thiên nhiên giao thoa',
        'Côn Đảo không chỉ là điểm đến của những ai muốn tìm về lịch sử hào hùng dân tộc '
            'mà còn là thiên đường sinh thái biển nguyên sơ bậc nhất Việt Nam. '
            'Nghĩa trang Hàng Dương khiến lòng người lắng lại, trong khi rạn san hô '
            'tại vườn quốc gia Côn Đảo mang đến những giây phút lặn biển kỳ diệu.',
        'PUBLISHED', 103, '2026-04-22 14:00:00', '2026-04-22 14:00:00'
    ),
    (
        4, 'FOOD',
        'Hải sản Nha Trang – Tươi sống và đậm vị biển khơi',
        'Chợ đêm Nha Trang về đêm rực rỡ ánh đèn và thơm lừng mùi hải sản nướng. '
            'Tôm hùm Alaska nướng bơ tỏi, cua Hoàng Đế hấp bia, sò điệp nướng mỡ hành – '
            'tất cả đều tươi rói, giá cả hợp lý so với chất lượng. '
            'Đây là điểm cộng lớn nhất khiến tôi muốn quay lại Nha Trang mỗi mùa hè.',
        'HIDDEN', 29, '2026-05-08 19:30:00', '2026-05-09 08:00:00'
    ),
    (
        3, 'NEWS',
        'Cảnh báo: Mùa mưa bão 2026 ảnh hưởng lịch trình tour miền Trung',
        'Trung tâm Khí tượng Thủy văn quốc gia dự báo mùa mưa bão 2026 tại miền Trung '
            'sẽ đến sớm hơn trung bình 2–3 tuần. Du khách có kế hoạch đến Đà Nẵng, Hội An, '
            'Huế từ tháng 9 nên theo dõi sát thông tin thời tiết và liên hệ đơn vị lữ hành '
            'để điều chỉnh lịch trình kịp thời.',
        'HIDDEN', 11, '2026-05-25 08:00:00', '2026-05-25 08:00:00'
    )
    ON CONFLICT DO NOTHING;
