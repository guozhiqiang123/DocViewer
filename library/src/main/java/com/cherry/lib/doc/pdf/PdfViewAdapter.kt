package com.cherry.lib.doc.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cherry.lib.doc.R
import com.cherry.lib.doc.interfaces.OnPdfItemClickListener
import com.cherry.lib.doc.util.ViewUtils.hide
import com.cherry.lib.doc.util.ViewUtils.show
import kotlinx.android.synthetic.main.list_item_pdf.view.container_view
import kotlinx.android.synthetic.main.list_item_pdf.view.pageView
import kotlinx.android.synthetic.main.pdf_view_page_loading_layout.view.pdf_view_page_loading_progress
import kotlinx.coroutines.Job

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfViewAdapter
 * Author: Victor
 * Date: 2023/09/28 11:17
 * Description: 
 * -----------------------------------------------------------------
 */

internal class PdfViewAdapter(
    private val renderer: PdfRendererCore?,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean,
    private val listener: OnPdfItemClickListener?
) : RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {

    private val jobMap = mutableMapOf<Int, Job>()

    private val renderSuccessMap = mutableMapOf<Int, Boolean>()

    private var isScrolling = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        return PdfPageViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.list_item_pdf, parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        try {
            return renderer?.getPageCount() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.itemView.container_view.setOnClickListener {
            listener?.OnPdfItemClick(holder.adapterPosition)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
                if (isScrolling) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val preFirstPosition = layoutManager.findFirstVisibleItemPosition()
                val preLastPosition = layoutManager.findLastVisibleItemPosition()
                for (index in preFirstPosition until preLastPosition + 1) {
                    if (renderSuccessMap[index] == true) continue
                    tryRenderPage(index) { pageNo, bitmap ->
                        if (bitmap == null) return@tryRenderPage
                        val finalFirstPosition = layoutManager.findFirstVisibleItemPosition()
                        val finalLastPosition = layoutManager.findLastVisibleItemPosition()
                        if (pageNo in finalFirstPosition until finalLastPosition + 1) {
                            val viewHolder =
                                recyclerView.findViewHolderForAdapterPosition(pageNo) as PdfPageViewHolder
                            renderPdfView(viewHolder, bitmap)
                        }
                    }
                }
            }
        })
    }

    override fun onViewAttachedToWindow(holder: PdfPageViewHolder) {
        super.onViewAttachedToWindow(holder)
        val itemView = holder.itemView
        if (enableLoadingForPages) {
            itemView.pdf_view_page_loading_progress.show()
        }
        if (isScrolling) {
            return
        }
        tryRenderPage(holder.adapterPosition) { pageNo, bitmap ->
            if (bitmap == null) return@tryRenderPage
            if (pageNo == holder.adapterPosition) {
                renderPdfView(holder, bitmap)
            }
        }
    }

    private fun tryRenderPage(pagePosition: Int, render: (Int, Bitmap?) -> Unit) {
        renderer?.renderPage(
            pagePosition,
            job = Job().also { jobMap[pagePosition] = it }) { bitmap: Bitmap?, pageNo: Int ->
            render(pageNo, bitmap)
        }
    }

    private fun renderPdfView(
        holder: PdfPageViewHolder,
        bitmap: Bitmap
    ) {
        val itemView = holder.itemView
        itemView.container_view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height =
                (itemView.container_view.width.toFloat() / ((bitmap.width.toFloat() / bitmap.height.toFloat()))).toInt()
            this.topMargin = pageSpacing.top
            this.leftMargin = pageSpacing.left
            this.rightMargin = pageSpacing.right
            this.bottomMargin = pageSpacing.bottom
        }
        itemView.pageView.setImageBitmap(bitmap)
        itemView.pageView.animation = AlphaAnimation(0F, 1F).apply {
            interpolator = LinearInterpolator()
            duration = 200
        }
        itemView.pdf_view_page_loading_progress.hide()
        renderSuccessMap[holder.adapterPosition] = true
    }

    override fun onViewDetachedFromWindow(holder: PdfPageViewHolder) {
        super.onViewDetachedFromWindow(holder)
        val itemView = holder.itemView
        val position = holder.adapterPosition
        jobMap.remove(position)?.cancel()
        renderSuccessMap[holder.adapterPosition] = false
        itemView.pageView.setImageBitmap(null)
        itemView.pageView.clearAnimation()
    }

    class PdfPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}