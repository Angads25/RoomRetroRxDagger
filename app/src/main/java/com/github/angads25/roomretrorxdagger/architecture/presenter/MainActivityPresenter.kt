package com.github.angads25.roomretrorxdagger.architecture.presenter

import android.content.Context

import android.support.v4.widget.SwipeRefreshLayout

import com.github.angads25.roomretrorxdagger.utils.Utility
import com.github.angads25.roomretrorxdagger.dagger.qualifier.ApplicationContext
import com.github.angads25.roomretrorxdagger.room.repository.PropertyDbRepository
import com.github.angads25.roomretrorxdagger.architecture.contract.PropertyContract
import com.github.angads25.roomretrorxdagger.retrofit.repository.PropertyApiRepository

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.android.schedulers.AndroidSchedulers

import java.util.Collections

import javax.inject.Inject

class MainActivityPresenter
@Inject constructor(@ApplicationContext val context: Context,
                    val propertyApiRepository: PropertyApiRepository,
                    val mainActivityView: PropertyContract.PropertyView,
                    val propertyDbRepository: PropertyDbRepository
) : PropertyContract.PropertyPresenter, SwipeRefreshLayout.OnRefreshListener {
    private val disposable = CompositeDisposable()

    override fun loadData() {
        mainActivityView.showProgress()
        val networkDisposable = Utility.isNetworkAvailable(context)
            .subscribe({
                val retroDisposable = propertyApiRepository
                        .getPropertyList()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map {
                            mainActivityView.hideProgress()
                            it.propertyListing
                        }
                        .switchMap {
                            return@switchMap Observable.just(it)
                                    .map {
                                        propertyDbRepository.deleteAll()
                                        propertyDbRepository.insertAll(it)
                                        it
                                    }
                                    .subscribeOn(Schedulers.io())
                        }
                        .doOnError {
                            it.printStackTrace()
                            val dbDisposable = propertyDbRepository
                                    .getProperties()
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .onErrorReturn {
                                        it.printStackTrace()
                                        Collections.emptyList()
                                    }
                                    .subscribe { mainActivityView.showData(it) }
                            disposable.add(dbDisposable)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ mainActivityView.showData(it) }, {
                            mainActivityView.hideProgress()
                            mainActivityView.onError(it)
                        })
                disposable.add(retroDisposable)
        }, {
            it.printStackTrace()
            val dbDisposable = propertyDbRepository
                    .getProperties()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturn {
                        mainActivityView.onError(it)
                        Collections.emptyList()
                    }
                    .doOnNext { mainActivityView.hideProgress() }
                    .subscribe({ mainActivityView.showData(it) }, { mainActivityView.onError(it) })
            disposable.add(dbDisposable)
        })
        disposable.add(networkDisposable)
    }

    override fun onRefresh() { mainActivityView.onRefresh() }

    override fun dumpData() { disposable.dispose() }
}
