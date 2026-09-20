import SwiftUI
import UIKit
import AVFoundation

/// The camera, as a view.
///
/// `UIImagePickerController` rather than a hand-built `AVCaptureSession`: the system one
/// already handles the flip, the flash, the shutter and the retake screen, and a camera built
/// from scratch here would be a worse version of all four for the sake of matching a colour.
struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void

    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = .camera
        controller.cameraDevice = .front
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let parent: CameraPicker

        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }

    /// False in the simulator and on the rare device without one, so the button can say why
    /// rather than opening a black rectangle.
    static var available: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }
}

/// Framing a picture for the avatar: pinch to zoom, drag to move, what is inside the circle
/// is kept.
///
/// In the app rather than through the system editor because the shape matters — the avatar is
/// a circle everywhere it appears, and a square crop leaves the parent guessing which corners
/// are about to go.
struct CropView: View {
    let image: UIImage
    let onCropped: (Data) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var zoom: CGFloat = 1
    @State private var committedZoom: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var committedOffset: CGSize = .zero

    /// The circle's diameter, and so the side of the square that comes out.
    private let frame: CGFloat = 300

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Text("Pinch to zoom, drag to move. What is inside the circle is kept.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                ZStack {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .scaleEffect(zoom)
                        .offset(offset)
                        .frame(width: frame, height: frame)
                        .clipShape(Circle())
                        .overlay(Circle().strokeBorder(.white, lineWidth: 3))
                        .clay(corner: frame / 2, elevation: 16, tint: accent.light)
                }
                .frame(width: frame, height: frame)
                .contentShape(Circle())
                .gesture(
                    SimultaneousGesture(
                        // Committed on end rather than tracked from zero, so a second pinch
                        // carries on from where the first left off instead of snapping back.
                        MagnificationGesture()
                            .onChanged { zoom = max(1, min(4, committedZoom * $0)) }
                            .onEnded { _ in committedZoom = zoom },
                        DragGesture()
                            .onChanged {
                                offset = CGSize(
                                    width: committedOffset.width + $0.translation.width,
                                    height: committedOffset.height + $0.translation.height
                                )
                            }
                            .onEnded { _ in committedOffset = offset }
                    )
                )

                Button("Start again") {
                    zoom = 1; committedZoom = 1
                    offset = .zero; committedOffset = .zero
                }
                .font(KFont.sans(13, .semibold))
                .foregroundStyle(accent.deep)

                Spacer()

                PrimaryButton(label: "Use photo") {
                    onCropped(cropped())
                    dismiss()
                }
                .padding(.horizontal, 20)
            }
            .padding(.top, 20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(KC.screen)
            .navigationTitle("Frame the photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    /// Renders what the circle is showing, at twice the on-screen size so it stays sharp on
    /// the largest place it is drawn.
    private func cropped() -> Data {
        let side = frame * 2
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        let output = renderer.image { _ in
            // The same maths SwiftUI used: fill the frame, then apply the zoom and the drag,
            // all doubled to match the output size.
            let scale = max(side / image.size.width, side / image.size.height) * zoom
            let drawn = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let origin = CGPoint(
                x: (side - drawn.width) / 2 + offset.width * 2,
                y: (side - drawn.height) / 2 + offset.height * 2
            )
            image.draw(in: CGRect(origin: origin, size: drawn))
        }
        // JPEG, not PNG: this is a photograph, and a PNG of one is several times the size for
        // no visible gain in a 52pt circle.
        return output.jpegData(compressionQuality: 0.9) ?? Data()
    }
}

/// Where a picture can come from, as one sheet.
struct PhotoSourceSheet: View {
    let hasPhoto: Bool
    let onCamera: () -> Void
    let onLibrary: () -> Void
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                source("Take a photo", "Use the camera now", "camera.fill",
                       enabled: CameraPicker.available) {
                    dismiss(); onCamera()
                }
                source("Choose from the library", "One you already have", "photo.on.rectangle",
                       enabled: true) {
                    dismiss(); onLibrary()
                }
                if hasPhoto {
                    Button("Remove the photo") { dismiss(); onRemove() }
                        .font(KFont.sans(14, .semibold))
                        .foregroundStyle(KC.danger)
                        .padding(.top, 4)
                }
                if !CameraPicker.available {
                    Text("This device has no camera Kilkari can use.")
                        .font(KFont.sans(12)).foregroundStyle(KC.faint)
                }
                Spacer()
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(KC.surface)
            .navigationTitle("Photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    private func source(
        _ title: String, _ subtitle: String, _ symbol: String,
        enabled: Bool, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                IconBadge(symbol: symbol, tint: accent.deep, background: accent.bg,
                          size: 40, corner: 12, iconSize: 18)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                    Text(subtitle).font(KFont.sans(12)).foregroundStyle(KC.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(KC.stoneLight)
            }
            .padding(14)
            .background(KC.screen)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(SpringPress())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}
