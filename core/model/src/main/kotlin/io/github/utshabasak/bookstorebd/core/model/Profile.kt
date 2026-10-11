package io.github.utshabasak.bookstorebd.core.model

/** The signed-in person's own profile, with the details only they (and the shop) can see. */
data class OwnProfile(
    val username: String,
    val email: String,
    /** A picture's address, or null when there is none. */
    val pictureUrl: String?,
    val buyerBannerUrl: String?,
    val sellerBannerUrl: String?,
    val sellerRating: Rating,
    val address: String,
    val phone: String,
    val bkashMerchant: String,
    val dateOfBirth: String,
    val gender: Gender?,
    val twoFactor: Boolean,
    /** False for an account made with Google that has not chosen a password yet. */
    val passwordSet: Boolean,
) {
    /** The steps the website's "Finish setting up" card lists, in its order. */
    fun setupSteps(mode: ProfileMode): List<SetupStep> =
        buildList {
            add(SetupStep(SetupItem.Photo, pictureUrl != null))
            add(SetupStep(SetupItem.Phone, phone.isNotBlank()))
            add(SetupStep(SetupItem.Address, address.isNotBlank()))
            add(SetupStep(SetupItem.DateOfBirth, dateOfBirth.isNotBlank()))
            val banner = if (mode == ProfileMode.Seller) sellerBannerUrl else buyerBannerUrl
            add(SetupStep(SetupItem.Banner, banner != null))
            add(SetupStep(SetupItem.TwoStep, twoFactor))
            if (mode == ProfileMode.Seller) add(SetupStep(SetupItem.Bkash, bkashMerchant.isNotBlank()))
        }

    val canSell: Boolean get() = bkashMerchant.isNotBlank()
}

data class Rating(
    val average: Double = 0.0,
    val count: Int = 0,
)

enum class Gender(
    val apiValue: String,
) {
    Male("male"),
    Female("female"),
}

/** The profile shows the buyer's or the seller's side of an account. */
enum class ProfileMode { Buyer, Seller }

enum class SetupItem { Photo, Phone, Address, DateOfBirth, Banner, TwoStep, Bkash }

data class SetupStep(
    val item: SetupItem,
    val done: Boolean,
)

/** A picture to upload: its bytes and type, already scaled down where that helps. */
class ImageUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

/** A change to one of the profile's pictures. */
sealed interface PictureChange {
    data object Keep : PictureChange

    data object Remove : PictureChange

    class Replace(
        val image: ImageUpload,
    ) : PictureChange
}

/**
 * Everything the edit form sends. As on the website, every field is sent and a
 * blank one clears it; the e-mail address cannot be changed.
 */
data class ProfileChanges(
    val username: String,
    val newPassword: String = "",
    val currentPassword: String = "",
    val address: String,
    val phone: String,
    val bkashMerchant: String,
    val dateOfBirth: String,
    val gender: Gender?,
    val picture: PictureChange = PictureChange.Keep,
    val buyerBanner: PictureChange = PictureChange.Keep,
    val sellerBanner: PictureChange = PictureChange.Keep,
)

/** People invited to the shop, and how many more can be invited today. */
data class Invites(
    val invited: List<Invitee>,
    val remainingToday: Int,
)

data class Invitee(
    val email: String,
    val invitedAt: String?,
    val joined: Boolean,
)

/** What deleting the account anonymised or removed, as the server reports it. */
data class AccountDeleted(
    val message: String,
)
