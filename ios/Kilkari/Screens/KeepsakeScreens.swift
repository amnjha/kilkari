import SwiftUI
import SwiftData
import PhotosUI

/// A page or two filed under a name: the birth certificate, a discharge summary, a
/// prescription. Stored in the app, not referenced, so nothing goes missing when the photo
/// library is tidied.
@Model
final class ScannedDocument {
    var title: String
    var tags: String?
    var filedOn: Date
    /// The pages themselves, in order.
    @Attribute(.externalStorage) var pages: [Data]

    init(title: String, tags: String? = nil, filedOn: Date = .now, pages: [Data] = []) {
        self.title = title
        self.tags = tags
        self.filedOn = filedOn
        self.pages = pages
    }
}

/// A photo album that lives somewhere else. Kilkari keeps the link, not the photos: a baby
/// tracker is the wrong place to store ten gigabytes of pictures.
@Model
final class Album {
    var title: String
    var note: String?
    var url: String

    init(title: String, note: String? = nil, url: String) {
        self.title = title
        self.note = note
        self.url = url
    }
}

/// A date that comes round: a birthday, a naming day, a first Diwali.
@Model
final class CalendarEvent {
    var title: String
    var note: String?
    var date: Date
    var annual: Bool

    init(title: String, note: String? = nil, date: Date, annual: Bool = true) {
        self.title = title
        self.note = note
        self.date = date
        self.annual = annual
    }

    /// The next time it falls, counting from today.
    func next(from today: Date = .now) -> Date {
        let cal = Calendar.current
        guard annual else { return date }
        var parts = cal.dateComponents([.month, .day], from: date)
        parts.year = cal.component(.year, from: today)
        let thisYear = cal.date(from: parts) ?? date
        if cal.startOfDay(for: thisYear) >= cal.startOfDay(for: today) { return thisYear }
        parts.year = (parts.year ?? 0) + 1
        return cal.date(from: parts) ?? thisYear
    }
}

// MARK: - Documents

struct DocumentsScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \ScannedDocument.filedOn, order: .reverse) private var documents: [ScannedDocument]

    @State private var picking: [PhotosPickerItem] = []
    @State private var pendingPages: [Data] = []
    @State private var naming = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if documents.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "folder.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("Nothing filed yet")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("Birth certificate, discharge summary, insurance. Kept in the app, so they survive a tidy-up of the photo library.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    ForEach(documents) { document in
                        KCard {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 12) {
                                    IconBadge(symbol: "doc.text.fill", tint: accent.deep,
                                              background: accent.bg, size: 40, corner: 12, iconSize: 18)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(document.title)
                                            .font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                                        Text("\(document.pages.count) \(Fmt.plural(document.pages.count, "page")) · \(Fmt.date(document.filedOn))")
                                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                    Spacer()
                                }
                                if !document.pages.isEmpty {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            ForEach(Array(document.pages.enumerated()), id: \.offset) { _, page in
                                                if let image = UIImage(data: page) {
                                                    Image(uiImage: image)
                                                        .resizable().scaledToFill()
                                                        .frame(width: 72, height: 96)
                                                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(14)
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PhotosPicker(selection: $picking, matching: .images) {
                Text("File a document")
                    .font(KFont.sans(15, .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).frame(height: 52)
                    .background(accent.main).clipShape(Capsule())
            }
            .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .onChange(of: picking) { _, items in
            guard !items.isEmpty else { return }
            Task {
                var pages: [Data] = []
                for item in items {
                    if let data = try? await item.loadTransferable(type: Data.self) { pages.append(data) }
                }
                pendingPages = pages
                picking = []
                if !pages.isEmpty { naming = true }
            }
        }
        .sheet(isPresented: $naming) {
            NameDocumentSheet(pageCount: pendingPages.count) { title, tags in
                context.insert(ScannedDocument(title: title, tags: tags, pages: pendingPages))
                pendingPages = []
                naming = false
            }
            .presentationDetents([.medium])
            .environment(\.accent, accent)
        }
    }
}

struct NameDocumentSheet: View {
    let pageCount: Int
    let onSave: (String, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var title = ""
    @State private var tags = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("\(pageCount) \(Fmt.plural(pageCount, "page")) picked.")
                        .font(KFont.sans(13)).foregroundStyle(KC.muted)
                    sheetField("Title", $title, "e.g. Birth certificate")
                    sheetField("Tags", $tags, "Legal, ID")
                    PrimaryButton(label: "File it",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                        let t = tags.trimmingCharacters(in: .whitespaces)
                        onSave(title.trimmingCharacters(in: .whitespaces), t.isEmpty ? nil : t)
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("File a document")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}

// MARK: - Albums

struct AlbumsScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \Album.title) private var albums: [Album]
    @State private var adding = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Albums live wherever you keep them. Kilkari holds the link, not the photos.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted)

                if albums.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "photo.stack.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("No albums linked yet")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("Paste a shared link and it shows up here.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    ForEach(albums) { album in
                        if let url = URL(string: album.url) {
                            Link(destination: url) { albumCard(album) }
                                .buttonStyle(SpringPress())
                        } else {
                            albumCard(album)
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Link an album") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddAlbumSheet { context.insert($0); adding = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }

    private func albumCard(_ album: Album) -> some View {
        KCard {
            HStack(spacing: 12) {
                IconBadge(symbol: "photo.stack.fill", tint: accent.deep,
                          background: accent.bg, size: 40, corner: 12, iconSize: 18)
                VStack(alignment: .leading, spacing: 1) {
                    Text(album.title).font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                    Text(album.note ?? album.url)
                        .font(KFont.sans(12)).foregroundStyle(KC.muted).lineLimit(1)
                }
                Spacer()
                Image(systemName: "arrow.up.right.square")
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(KC.stoneLight)
            }
            .padding(14)
        }
    }
}

struct AddAlbumSheet: View {
    let onSave: (Album) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var title = ""
    @State private var note = ""
    @State private var url = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("Title", $title, "e.g. First month")
                    sheetField("Note", $note, "e.g. 184 photos")
                    sheetField("Link", $url, "https://…")
                    PrimaryButton(label: "Save album",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty
                                        && !url.trimmingCharacters(in: .whitespaces).isEmpty) {
                        let n = note.trimmingCharacters(in: .whitespaces)
                        onSave(Album(title: title.trimmingCharacters(in: .whitespaces),
                                     note: n.isEmpty ? nil : n,
                                     url: url.trimmingCharacters(in: .whitespaces)))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Link an album")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}

// MARK: - Events

struct EventsScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query private var events: [CalendarEvent]
    @State private var adding = false

    /// The birthday is not stored: it is the date of birth, every year, and storing it would
    /// mean two places to correct when the birthday is wrong.
    private var all: [(title: String, note: String?, next: Date, stored: CalendarEvent?)] {
        let birthday = CalendarEvent(title: "\(baby.name)'s birthday", date: baby.dob, annual: true)
        return ([(birthday.title, "Every year", birthday.next(), nil)]
            + events.map { ($0.title, $0.note, $0.next(), $0) })
            .sorted { $0.next < $1.next }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(all.enumerated()), id: \.offset) { i, event in
                            let days = Calendar.current.dateComponents(
                                [.day], from: Calendar.current.startOfDay(for: .now),
                                to: Calendar.current.startOfDay(for: event.next)
                            ).day ?? 0
                            HStack(spacing: 12) {
                                IconBadge(symbol: event.stored == nil ? "birthday.cake.fill" : "calendar",
                                          tint: accent.deep, background: accent.bg,
                                          size: 38, corner: 11, iconSize: 17)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(event.title)
                                        .font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                                    Text([event.note, Fmt.date(event.next)]
                                        .compactMap { $0 }.joined(separator: " · "))
                                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                }
                                Spacer()
                                Text(days == 0 ? "today" : "\(days) \(Fmt.plural(days, "day"))")
                                    .font(KFont.sans(12, .semibold))
                                    .foregroundStyle(days <= 7 ? accent.deep : KC.muted)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 11)
                            .swipeActions {
                                if let stored = event.stored {
                                    Button("Delete", role: .destructive) { context.delete(stored) }
                                }
                            }
                            if i != all.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Add a date") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddEventSheet { context.insert($0); adding = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }
}

struct AddEventSheet: View {
    let onSave: (CalendarEvent) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var title = ""
    @State private var note = ""
    @State private var date = Date.now
    @State private var annual = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("What", $title, "e.g. Diwali")
                    sheetRow("When") {
                        DatePicker("", selection: $date, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }
                    Picker("", selection: $annual) {
                        Text("One-off").tag(false)
                        Text("Every year").tag(true)
                    }
                    .pickerStyle(.segmented)
                    sheetField("Note", $note, "Optional detail")
                    PrimaryButton(label: "Save the date",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                        let n = note.trimmingCharacters(in: .whitespaces)
                        onSave(CalendarEvent(title: title.trimmingCharacters(in: .whitespaces),
                                             note: n.isEmpty ? nil : n,
                                             date: date, annual: annual))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Add a date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
