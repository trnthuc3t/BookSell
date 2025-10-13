package com.pro.book.model

class Feedback {
    var name: String? = null
    var phone: String? = null
    @JvmField
    var email: String? = null
    @JvmField
    var comment: String? = null
    @JvmField
    var content: String? = null
    @JvmField
    var rate: Double = 0.0
    @JvmField
    var userEmail: String? = null

    constructor()

    constructor(name: String?, phone: String?, email: String?, comment: String?) {
        this.name = name
        this.phone = phone
        this.email = email
        this.comment = comment
        this.content = comment
        this.userEmail = email
    }

    constructor(name: String?, phone: String?, email: String?, comment: String?, rate: Double) {
        this.name = name
        this.phone = phone
        this.email = email
        this.comment = comment
        this.content = comment
        this.rate = rate
        this.userEmail = email
    }
}
