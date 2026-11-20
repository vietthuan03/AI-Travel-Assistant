# AiTravelAssistant · Android

Mở thư mục gốc `AiTravelAssistant/` trong Android Studio và chọn cấu hình `app` để phát triển ứng dụng Jetpack Compose. `native/` là một dự án Kotlin/XML có Gradle wrapper, application ID và luồng lưu chuyến đi riêng. Android Studio liên kết cả hai Gradle project khi mở thư mục gốc. Để build ứng dụng XML từ gốc, chạy `./native/gradlew -p native :app:assembleDebug`.

## Chạy ứng dụng và test

Dùng Gradle wrapper ở thư mục gốc, JDK 21 và Android SDK Platform 37. File cấu hình Firebase của ứng dụng Compose nằm tại `app/google-services.json`. Luồng tạo lịch trình gọi Firebase AI Logic và cần kết nối mạng. Khi Firebase yêu cầu App Check, thiết bị debug cần được đăng ký với project Firebase tương ứng. Các unit test dùng dữ liệu giả và chạy ngoại tuyến sau khi Gradle đã tải các dependency.

| Mục đích | Lệnh từ thư mục gốc |
|---|---|
| Build APK debug và chạy unit test | `./gradlew :app:assembleDebug :app:testDebugUnitTest` |
| Cài bản debug lên thiết bị hoặc emulator | `./gradlew :app:installDebug` |
| Chạy kiểm thử tích hợp Room trên Android | `./gradlew :app:connectedDebugAndroidTest` |

## Tìm đúng chỗ để sửa

Các đường dẫn dưới đây tính từ `app/src/main/java/com/example/ai_travel_assistant/`.

| Khi cần sửa | Bắt đầu ở |
|---|---|
| Điểm vào, chuyển giữa Planner và Lịch sử, khởi tạo dependencies | `MainActivity.kt` và `app/TravelApp.kt` |
| Trường nhập, ngày đi/về, ngân sách và nút tạo | `feature/planner/PlannerForm.kt`, `TripDetailsFields.kt` và `PlannerScaffold.kt` |
| Điều kiện bắt buộc để tạo chuyến | `domain/trip/TripRequest.kt` |
| Quy tắc tạo và tự lưu | `domain/trip/GenerateTrip.kt` |
| Prompt, lỗi Firebase, thứ tự model và thời gian chờ | `data/ai/TripPrompt.kt`, `FirebaseTripGenerator.kt` và `TravelModelFallback.kt` |
| Cấu trúc phản hồi AI, định dạng lịch trình | `data/ai/ItineraryParser.kt` đọc dữ liệu. `ui/itinerary/` hiển thị tuyến đi và timeline dùng chung. |
| Danh sách, chi tiết và xác nhận xóa | `feature/history/HistoryViewModel.kt` và `TripHistoryDialog.kt` |
| Truy vấn SQL, schema, ánh xạ bản ghi | `data/local/TripsDatabase.kt` và `RoomTripRepository.kt`. Schema đã xuất nằm tại `app/schemas/`. |
| Nội dung tiếng Anh và tiếng Việt | `app/src/main/res/values/strings.xml` và `values-vi/strings.xml` |

## Theo dõi một lần tạo lịch trình

1. `PlannerForm` tạo `TripRequest`. `PlannerViewModel` kiểm tra dữ liệu, phát trạng thái đang tải và gọi `GenerateTrip`.
2. `FirebaseTripGenerator` xây prompt, áp dụng chính sách fallback và đọc phản hồi thành `Itinerary`.
3. Khi phản hồi có timeline hợp lệ, `GenerateTrip` yêu cầu lưu qua `RoomTripRepository`. Lỗi ghi trả về kết quả với trạng thái chưa lưu. Phản hồi dạng văn bản vẫn được hiển thị và bỏ qua bước ghi.
4. `HistoryViewModel` đọc các chuyến đã ghi từ `TripRepository`. Mở một chuyến đã lưu sẽ đọc dữ liệu cục bộ và hiển thị bằng cùng thẻ lịch trình với Planner.

## Thêm tính năng

Đặt màn hình, UI state và ViewModel trong `feature/<tên tính năng>/`. Đặt quy tắc cần kiểm thử độc lập trong `domain/`. Phần giao tiếp SDK hoặc database thuộc `data/`. Truyền các phụ thuộc qua constructor và nối chúng tại `app/TravelApp.kt`. Domain có thể chạy trên JVM, UI nhận model nghiệp vụ, còn data chịu trách nhiệm chuyển đổi định dạng bên ngoài.

Unit test nằm trong `app/src/test/`. Kiểm thử dùng cơ sở dữ liệu Android nằm trong `app/src/androidTest/`. Khi sửa cấu trúc bảng, cập nhật phiên bản Room, migration và kiểm thử đọc lại dữ liệu từ phiên bản cũ.
